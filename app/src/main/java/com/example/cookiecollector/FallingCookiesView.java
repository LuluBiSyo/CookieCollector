package com.example.cookiecollector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * Swipe game board: cookies fall from the top of the screen. A cookie is cut when one
 * swipe/flick stroke enters it from outside and leaves it again (i.e. crosses it).
 * Cut cookies split into two halves; the stroke is drawn as a knife slash.
 */
public class FallingCookiesView extends View {

    interface Listener {
        void onCookieCrossed(Cookie cookie, float x, float y);
        void onCookieMissed(Cookie cookie);
        void onAllCookiesDone();
    }

    static class Cookie {
        int number;
        float x;
        float y;
        float angle;      // degrees
        float spin;       // degrees per ms
        // Time the cookie has been falling, excluding pauses
        long fallingMillis;
        // How long this cookie takes to cross the whole screen; set when it appears
        long fallMillis;
        // Closest the cutting stroke came to the cookie's center, in px
        float minOffsetPx = Float.MAX_VALUE;
        // Set while the current stroke is inside the cookie after entering it from outside
        boolean entered;
        boolean touchedAtDown;
    }

    // One half of a cut cookie
    private static class Half {
        float x, y, vx, vy;
        float cutAngle;   // degrees, direction of the cut
        float angle;      // cookie's own rotation relative to the cut
        float rot;        // spin accumulated since the cut
        float spin;
        int side;         // -1 or 1
        long age;
    }

    // Starts slow enough for older adults: a cookie takes 9 s to cross the screen,
    // and a new one appears every 2.5 s, so about 3-4 are on screen at once.
    // The game can speed up; cookies then appear more often by the same factor.
    static final long START_FALL_MILLIS = 9000;
    private static final long START_SPAWN_INTERVAL_MILLIS = 2500;
    // Free space kept between a cookie's edge and the screen edge, so a swipe can start
    // outside the cookie on either side
    private static final float EDGE_MARGIN_MM = 12f;
    private static final long TRAIL_MILLIS = 180;
    private static final long HALF_MILLIS = 900;
    private static final float GRAVITY = 0.0025f; // px per ms^2, for the halves

    private final ArrayList<Cookie> cookies = new ArrayList<>();
    private final ArrayList<Half> halves = new ArrayList<>();
    private final ArrayList<float[]> trail = new ArrayList<>(); // {x, y, time}
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint bladeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bladeCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final GameEffects effects;
    private final float density;

    private Listener listener;
    private Bitmap cookieBitmap;
    private float radiusPx;
    private int totalCookies;
    private int spawnedCookies = 0;
    private boolean running = false;
    private boolean done = false;
    private long lastFrameTime;
    private long sinceLastSpawn;
    private long fallMillis = START_FALL_MILLIS;
    private float lastX;
    private float lastY;

    public FallingCookiesView(Context context) { this(context, null); }

    public FallingCookiesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        effects = new GameEffects(context);
        bladeGlowPaint.setColor(0xFF9FD8FF);
        bladeGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        bladeCorePaint.setColor(0xFFFFFFFF);
        bladeCorePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setup(float radiusPx, int totalCookies, Listener listener) {
        this.radiusPx = radiusPx;
        this.totalCookies = totalCookies;
        this.listener = listener;
        int d = Math.max(1, (int) (2 * radiusPx));
        cookieBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.cookie), d, d, true);
        // Spawn the first cookie as soon as the game starts
        sinceLastSpawn = START_SPAWN_INTERVAL_MILLIS;
    }

    /** Fall time for cookies that appear from now on; cookies already falling keep their speed. */
    void setFallMillis(long fallMillis) { this.fallMillis = fallMillis; }

    GameEffects getEffects() { return effects; }
    float getRadiusPx() { return radiusPx; }

    void start() {
        if (done) return;
        running = true;
        lastFrameTime = SystemClock.uptimeMillis();
        invalidate();
    }

    void pause() {
        running = false;
        trail.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) return;

        long now = SystemClock.uptimeMillis();
        // Cap the step so a stalled frame doesn't teleport cookies
        long dt = Math.min(now - lastFrameTime, 50);
        lastFrameTime = now;
        step(dt);

        for (Cookie c : cookies) {
            canvas.save();
            canvas.rotate(c.angle, c.x, c.y);
            canvas.drawBitmap(cookieBitmap, c.x - radiusPx, c.y - radiusPx, bitmapPaint);
            canvas.restore();
        }
        drawHalves(canvas);
        drawBlade(canvas, now);
        effects.draw(canvas);

        if (running) postInvalidateOnAnimation();
    }

    private void step(long dt) {
        sinceLastSpawn += dt;
        long spawnInterval = START_SPAWN_INTERVAL_MILLIS * fallMillis / START_FALL_MILLIS;
        if (spawnedCookies < totalCookies && sinceLastSpawn >= spawnInterval) {
            sinceLastSpawn = 0;
            Cookie c = new Cookie();
            c.number = spawnedCookies++;
            c.fallMillis = fallMillis;
            // Keep away from the side edges; on a very narrow screen fall back to the middle
            float margin = Math.min(radiusPx + GameUtils.mmToPx(getContext(), EDGE_MARGIN_MM), getWidth() / 2f);
            c.x = margin + random.nextFloat() * Math.max(0, getWidth() - 2 * margin);
            c.y = -radiusPx;
            c.angle = random.nextFloat() * 360;
            c.spin = (random.nextFloat() - 0.5f) * 0.09f;
            cookies.add(c);
        }

        Iterator<Cookie> it = cookies.iterator();
        while (it.hasNext()) {
            Cookie c = it.next();
            c.y += fallSpeed(c) * dt;
            c.angle += c.spin * dt;
            c.fallingMillis += dt;
            if (c.y - radiusPx > getHeight()) {
                it.remove();
                effects.popup("Miss", c.x, getHeight() - 3 * radiusPx, GameEffects.RED, 28);
                if (listener != null) listener.onCookieMissed(c);
            }
        }

        Iterator<Half> hi = halves.iterator();
        while (hi.hasNext()) {
            Half h = hi.next();
            h.age += dt;
            h.vy += GRAVITY * dt;
            h.x += h.vx * dt;
            h.y += h.vy * dt;
            h.rot += h.spin * dt;
            if (h.age > HALF_MILLIS) hi.remove();
        }
        effects.update(dt);

        if (spawnedCookies == totalCookies && cookies.isEmpty() && halves.isEmpty() && effects.isEmpty()) {
            done = true;
            running = false;
            if (listener != null) listener.onAllCookiesDone();
        }
    }

    private float fallSpeed(Cookie c) { return (getHeight() + 2 * radiusPx) / c.fallMillis; } // px per ms

    private void drawHalves(Canvas canvas) {
        for (Half h : halves) {
            float f = (float) h.age / HALF_MILLIS;
            bitmapPaint.setAlpha((int) (255 * (1 - f * f)));
            canvas.save();
            canvas.translate(h.x, h.y);
            canvas.rotate(h.cutAngle + h.rot);
            // Keep the half on one side of the cut line
            if (h.side < 0) canvas.clipRect(-radiusPx, -radiusPx, radiusPx, 0);
            else canvas.clipRect(-radiusPx, 0, radiusPx, radiusPx);
            canvas.rotate(h.angle);
            canvas.drawBitmap(cookieBitmap, -radiusPx, -radiusPx, bitmapPaint);
            canvas.restore();
        }
        bitmapPaint.setAlpha(255);
    }

    // A tapered slash: thick and bright at the finger, thin and faded at the tail
    private void drawBlade(Canvas canvas, long now) {
        Iterator<float[]> it = trail.iterator();
        while (it.hasNext()) if (now - it.next()[2] > TRAIL_MILLIS) it.remove();
        float maxWidth = 7 * density;
        for (int i = 1; i < trail.size(); i++) {
            float[] a = trail.get(i - 1);
            float[] b = trail.get(i);
            float life = 1 - (float) (now - b[2]) / TRAIL_MILLIS;
            if (life <= 0) continue;
            bladeGlowPaint.setStrokeWidth(maxWidth * 2.2f * life);
            bladeGlowPaint.setAlpha((int) (90 * life));
            canvas.drawLine(a[0], a[1], b[0], b[1], bladeGlowPaint);
            bladeCorePaint.setStrokeWidth(maxWidth * life);
            bladeCorePaint.setAlpha((int) (255 * life));
            canvas.drawLine(a[0], a[1], b[0], b[1], bladeCorePaint);
        }
    }

    /** Splits the cookie into two halves that fly apart on either side of the cut. */
    private void cut(Cookie c, float dirX, float dirY) {
        float len = (float) Math.hypot(dirX, dirY);
        if (len == 0) { dirX = 1; dirY = 0; len = 1; }
        float ux = dirX / len;
        float uy = dirY / len;
        float cutAngle = (float) Math.toDegrees(Math.atan2(uy, ux));
        float push = 0.12f * density; // px per ms, sideways from the cut line
        for (int side = -1; side <= 1; side += 2) {
            Half h = new Half();
            h.x = c.x;
            h.y = c.y;
            // Normal to the cut, pointing to this half's side
            h.vx = -uy * side * push + ux * 0.03f * density;
            h.vy = ux * side * push + uy * 0.03f * density + fallSpeed(c) - 0.1f * density;
            h.cutAngle = cutAngle;
            h.angle = c.angle - cutAngle;
            h.spin = side * 0.25f;
            h.side = side;
            halves.add(h);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!running) return true;
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                trail.clear();
                // A stroke that starts on a cookie has not crossed into it
                for (Cookie c : cookies) {
                    c.entered = false;
                    c.touchedAtDown = inside(c, x, y);
                    c.minOffsetPx = Float.MAX_VALUE;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int h = 0; h < e.getHistorySize(); h++) {
                    moveTo(e.getHistoricalX(h), e.getHistoricalY(h));
                }
                moveTo(x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                for (Cookie c : cookies) c.entered = false;
                break;
        }
        lastX = x;
        lastY = y;
        return true;
    }

    private void moveTo(float x, float y) {
        Iterator<Cookie> it = cookies.iterator();
        while (it.hasNext()) {
            Cookie c = it.next();
            if (c.touchedAtDown) {
                if (!inside(c, x, y)) c.touchedAtDown = false;
                continue;
            }
            if (!c.entered && segmentHitsCookie(c, lastX, lastY, x, y)) c.entered = true;
            // Track how close to the center the cut passes, for the accuracy bonus
            if (c.entered) c.minOffsetPx = Math.min(c.minOffsetPx, distanceToSegment(c, lastX, lastY, x, y));
            if (c.entered && !inside(c, x, y)) {
                it.remove();
                cut(c, x - lastX, y - lastY);
                if (listener != null) listener.onCookieCrossed(c, c.x, c.y);
            }
        }
        trail.add(new float[]{x, y, SystemClock.uptimeMillis()});
        lastX = x;
        lastY = y;
    }

    private boolean inside(Cookie c, float x, float y) {
        float dx = x - c.x;
        float dy = y - c.y;
        return dx * dx + dy * dy <= radiusPx * radiusPx;
    }

    private boolean segmentHitsCookie(Cookie c, float x1, float y1, float x2, float y2) {
        return distanceToSegment(c, x1, y1, x2, y2) <= radiusPx;
    }

    /** Distance from the cookie's center to the closest point of a stroke segment, in px. */
    private float distanceToSegment(Cookie c, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lenSq = dx * dx + dy * dy;
        float t = lenSq == 0 ? 0 : ((c.x - x1) * dx + (c.y - y1) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        return (float) Math.hypot(x1 + t * dx - c.x, y1 + t * dy - c.y);
    }
}
