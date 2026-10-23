package com.example.cookiecollector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Spiral game board: a spiral tunnel winding out from the center of the screen. The player
 * drags the cookie from the center to the exit. When the cookie touches a wall, that counts
 * as a bump. Every touch sample is logged so tracing accuracy can be analysed later.
 * Sizes are in real millimetres; if the spiral doesn't fit the screen, everything is shrunk
 * by the same factor and that factor is reported.
 */
public class SpiralTunnelView extends View {

    interface Listener {
        void onCollect();
        void onBumpStart();
        void onSpiralEscaped(Result result);
    }

    static class Result {
        int spiral;
        boolean clockwise;
        long millis;
        int bumps;
        long bumpMillis;
        float meanDeviationMm;
        float maxDeviationMm;
        int lifts;
    }

    private static final float WALL_MM = 1.5f;
    private static final float START_RADIUS_MM = 5f;
    private static final float TURNS = 1.5f;
    private static final float SAMPLE_STEP_MM = 0.4f;
    // How far along the tunnel the cookie may move between two touch samples; stops shortcuts through walls
    private static final float SEARCH_WINDOW_MM = 12f;
    private static final float SPRINKLE_SPACING_MM = 8f;
    // The cookie must move this far back from the wall before a new bump can start
    private static final float HYSTERESIS_MM = 0.3f;
    private static final float MARGIN_MM = 1.5f;
    private static final long FLASH_MILLIS = 400;
    private static final int[] SPRINKLE_COLORS = {0xFFFF80AB, 0xFF80D8FF, 0xFFFFE57F, 0xFFB9F6CA};

    private final float mmPx;
    private final float density;
    private final GameEffects effects;
    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint channelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint channelCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    private Listener listener;
    private StringBuilder log;
    private Bitmap cookieBitmap;
    private float tunnelMm;
    private float cookieMm;
    private float topReservePx;

    // Current spiral
    private int spiral;
    private boolean clockwise;
    private float rotation;
    private boolean needsBuild = false;
    private float scale = 1;
    private float k;            // pixels per design mm (mmPx * scale)
    private float cx, cy;
    private float[] px, py, arc;
    private int n;
    private final Path tunnelPath = new Path();
    private final Path trailPath = new Path();
    private float[] sprinkleArc;
    private int[] sprinkleIdx;
    private int[] sprinkleState; // 0 waiting, 1 collected, 2 lost (passed while bumping)
    private int nextSprinkle;

    // Play state
    private boolean active = false;
    private boolean dragging = false;
    private boolean started = false;
    private boolean escaped = false;
    private int nearestIdx;
    private int maxIdx;
    private float cookieX, cookieY, cookieAngle;
    private boolean inContact = false;
    private long trialStart, pausedAt, pausedTotal, contactStart, flashUntil, lastHint;
    private long lastFrameTime = -1;
    private int bumps, lifts;
    private long bumpMillis;
    private double deviationSum;
    private float maxDeviation;
    private int deviationCount;

    public SpiralTunnelView(Context context) { this(context, null); }

    public SpiralTunnelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mmPx = GameUtils.mmToPx(context, 1);
        density = getResources().getDisplayMetrics().density;
        effects = new GameEffects(context);
        topReservePx = 64 * density;
        for (Paint p : new Paint[]{wallPaint, channelPaint, trailPaint}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        // Square ends so the exit is open; the closed start is drawn as a round cap separately
        wallPaint.setStrokeCap(Paint.Cap.BUTT);
        channelPaint.setStrokeCap(Paint.Cap.BUTT);
        channelPaint.setColor(0xFF08121D);
        channelCapPaint.setColor(0xFF08121D);
        trailPaint.setColor(0x66FFD54F);
        ringPaint.setStyle(Paint.Style.STROKE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(GameEffects.GOLD);
        textPaint.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
    }

    void setup(float tunnelMm, float cookieMm, StringBuilder log, Listener listener) {
        this.tunnelMm = tunnelMm;
        this.cookieMm = cookieMm;
        this.log = log;
        this.listener = listener;
    }

    float getScale() { return scale; }

    /** Starts a new spiral; it is laid out on the next frame. */
    void newSpiral(int spiral, boolean clockwise) {
        this.spiral = spiral;
        this.clockwise = clockwise;
        rotation = (float) (Math.random() * 2 * Math.PI);
        needsBuild = true;
        started = dragging = escaped = inContact = false;
        bumps = lifts = 0;
        bumpMillis = 0;
        deviationSum = 0;
        deviationCount = 0;
        maxDeviation = 0;
        nearestIdx = maxIdx = 0;
        pausedTotal = 0;
        invalidate();
    }

    void start() {
        active = true;
        if (started && pausedAt > 0) pausedTotal += SystemClock.uptimeMillis() - pausedAt;
        pausedAt = 0;
        invalidate();
    }

    void pause() {
        long now = SystemClock.uptimeMillis();
        if (dragging) {
            // Pausing mid-drag ends the drag, as if the finger was lifted
            dragging = false;
            lifts++;
            endContact(now);
        }
        active = false;
        pausedAt = now;
        invalidate();
    }

    private void build() {
        needsBuild = false;
        float halfTunnel = tunnelMm / 2;
        float pitch = tunnelMm + WALL_MM;
        float outerMm = START_RADIUS_MM + pitch * TURNS + halfTunnel + WALL_MM;
        float availableMm = Math.min(getWidth(), getHeight() - topReservePx) / 2f / mmPx - MARGIN_MM;
        scale = Math.min(1f, availableMm / outerMm);
        k = mmPx * scale;
        cx = getWidth() / 2f;
        cy = topReservePx + (getHeight() - topReservePx) / 2f;

        double thetaMax = 2 * Math.PI * TURNS;
        int capacity = (int) (2 * Math.PI * TURNS * (START_RADIUS_MM + pitch * TURNS) / SAMPLE_STEP_MM) + 10;
        px = new float[capacity];
        py = new float[capacity];
        arc = new float[capacity];
        n = 0;
        double theta = 0;
        int sign = clockwise ? 1 : -1;
        while (theta <= thetaMax && n < capacity) {
            double r = START_RADIUS_MM + pitch * theta / (2 * Math.PI);
            double a = rotation + sign * theta;
            px[n] = cx + (float) (r * k * Math.cos(a));
            py[n] = cy + (float) (r * k * Math.sin(a));
            arc[n] = n == 0 ? 0 : arc[n - 1] + (float) Math.hypot(px[n] - px[n - 1], py[n] - py[n - 1]) / k;
            n++;
            theta += SAMPLE_STEP_MM / r;
        }

        tunnelPath.reset();
        tunnelPath.moveTo(px[0], py[0]);
        for (int i = 1; i < n; i++) tunnelPath.lineTo(px[i], py[i]);
        wallPaint.setStrokeWidth((tunnelMm + 2 * WALL_MM) * k);
        channelPaint.setStrokeWidth(tunnelMm * k);
        trailPaint.setStrokeWidth(tunnelMm * 0.25f * k);

        int count = (int) ((arc[n - 1] - 4) / SPRINKLE_SPACING_MM);
        sprinkleArc = new float[count];
        sprinkleIdx = new int[count];
        sprinkleState = new int[count];
        nextSprinkle = 0;
        for (int s = 0, i = 0; s < count; s++) {
            sprinkleArc[s] = (s + 1) * SPRINKLE_SPACING_MM;
            while (i < n - 1 && arc[i] < sprinkleArc[s]) i++;
            sprinkleIdx[s] = i;
        }

        int d = Math.max(1, (int) (cookieMm * k));
        cookieBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.cookie), d, d, true);
        cookieX = px[0];
        cookieY = py[0];
        cookieAngle = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needsBuild && getWidth() > 0) build();
        if (px == null) return;

        long now = SystemClock.uptimeMillis();
        long dt = lastFrameTime < 0 ? 0 : Math.min(now - lastFrameTime, 50);
        lastFrameTime = now;
        effects.update(dt);

        // Walls glow red while the cookie is touching them, then fade back to caramel
        float flash = inContact ? 1 : Math.max(0, (float) (flashUntil - now) / FLASH_MILLIS);
        wallPaint.setColor(blend(0xFFC8874A, 0xFFFF3B3B, flash));
        wallCapPaint.setColor(wallPaint.getColor());
        canvas.drawCircle(px[0], py[0], wallPaint.getStrokeWidth() / 2, wallCapPaint);
        canvas.drawPath(tunnelPath, wallPaint);
        canvas.drawCircle(px[0], py[0], channelPaint.getStrokeWidth() / 2, channelCapPaint);
        canvas.drawPath(tunnelPath, channelPaint);

        // Crumb trail behind the cookie
        if (maxIdx > 0) {
            trailPath.reset();
            trailPath.moveTo(px[0], py[0]);
            for (int i = 1; i <= maxIdx; i++) trailPath.lineTo(px[i], py[i]);
            canvas.drawPath(trailPath, trailPaint);
        }

        // Sprinkles waiting to be collected twinkle gently
        for (int s = 0; s < sprinkleState.length; s++) {
            if (sprinkleState[s] != 0) continue;
            int i = sprinkleIdx[s];
            dotPaint.setColor(SPRINKLE_COLORS[s % SPRINKLE_COLORS.length]);
            dotPaint.setAlpha((int) (180 + 75 * Math.sin(now / 200.0 + s)));
            canvas.drawCircle(px[i], py[i], 0.8f * k, dotPaint);
        }

        drawExit(canvas, now);

        if (!started) {
            // Pulsing ring that says "drag me"
            float pulse = (float) (0.5 + 0.5 * Math.sin(now / 200.0));
            ringPaint.setColor(0xFFFFFFFF);
            ringPaint.setAlpha((int) (120 + 135 * pulse));
            ringPaint.setStrokeWidth(2 * density);
            canvas.drawCircle(cookieX, cookieY, cookieMm * k * (0.75f + 0.25f * pulse), ringPaint);
        }
        if (inContact) {
            ringPaint.setColor(0xFFFF3B3B);
            ringPaint.setStrokeWidth(2.5f * density);
            canvas.drawCircle(cookieX, cookieY, cookieMm * k * 0.6f, ringPaint);
        }
        float r = cookieMm * k / 2;
        canvas.save();
        canvas.rotate(cookieAngle, cookieX, cookieY);
        canvas.drawBitmap(cookieBitmap, cookieX - r, cookieY - r, bitmapPaint);
        canvas.restore();

        effects.draw(canvas);

        if (active || !effects.isEmpty() || flash > 0) postInvalidateOnAnimation();
        else lastFrameTime = -1;
    }

    private void drawExit(Canvas canvas, long now) {
        float ex = px[n - 1];
        float ey = py[n - 1];
        float pulse = (float) (0.85 + 0.15 * Math.sin(now / 250.0));
        float glowR = tunnelMm * k * 1.1f * pulse;
        glowPaint.setShader(new RadialGradient(ex, ey, glowR,
                new int[]{0xA0FFD54F, 0x00FFD54F}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(ex, ey, glowR, glowPaint);
        // Label just outside the spiral, away from the center
        float dx = ex - cx;
        float dy = ey - cy;
        float len = (float) Math.hypot(dx, dy);
        float off = tunnelMm * k * 1.3f;
        canvas.drawText("EXIT", ex + dx / len * off, ey + dy / len * off + textPaint.getTextSize() / 3, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!active || escaped || px == null) return true;
        float x = e.getX();
        float y = e.getY();
        long t = e.getEventTime();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Generous grab area: older players don't need to land exactly on the cookie
                if (Math.hypot(x - cookieX, y - cookieY) <= cookieMm * k * 1.25f) {
                    dragging = true;
                    if (!started) {
                        started = true;
                        trialStart = t;
                        pausedTotal = 0;
                    }
                    process(x, y, t, "down");
                }
                else if (t - lastHint > 1500) {
                    lastHint = t;
                    effects.popup("Touch the cookie", cookieX, cookieY - cookieMm * k * 1.5f, GameEffects.WHITE, 22);
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) break;
                for (int h = 0; h < e.getHistorySize() && dragging; h++) {
                    process(e.getHistoricalX(h), e.getHistoricalY(h), e.getHistoricalEventTime(h), "move");
                }
                if (dragging) process(x, y, t, "move");
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) break;
                process(x, y, t, "up");
                if (dragging) {
                    dragging = false;
                    lifts++;
                    endContact(t);
                }
                break;
        }
        return true;
    }

    private void process(float x, float y, long t, String event) {
        // Nearest point on the tunnel's center line, searching only near the last one
        int window = (int) (SEARCH_WINDOW_MM / SAMPLE_STEP_MM);
        int lo = Math.max(0, nearestIdx - window);
        int hi = Math.min(n - 1, nearestIdx + window);
        int best = nearestIdx;
        float bestSq = Float.MAX_VALUE;
        for (int i = lo; i <= hi; i++) {
            float dx = x - px[i];
            float dy = y - py[i];
            float sq = dx * dx + dy * dy;
            if (sq < bestSq) {
                bestSq = sq;
                best = i;
            }
        }
        nearestIdx = best;
        float distPx = (float) Math.sqrt(bestSq);
        long elapsed = t - trialStart - pausedTotal;

        // Done as soon as the cookie's edge reaches the open end, even if it is touching a wall
        if (arc[best] + cookieMm / 2 >= arc[n - 1]) {
            logSample(x, y, elapsed, event, best, 0);
            endContact(t);
            escape(elapsed);
            invalidate();
            return;
        }
        float maxDevPx = (tunnelMm / 2 - cookieMm / 2) * k;

        // Positive when the finger is on the outer side of the center line
        int a = Math.max(0, best - 1);
        int b = Math.min(n - 1, best + 1);
        float cross = (px[b] - px[a]) * (y - py[best]) - (py[b] - py[a]) * (x - px[best]);
        float signedDevMm = (clockwise ? -1 : 1) * Math.signum(cross) * distPx / mmPx;

        if (!inContact && distPx > maxDevPx) {
            inContact = true;
            bumps++;
            contactStart = t;
            flashUntil = SystemClock.uptimeMillis() + FLASH_MILLIS;
            effects.popup("Bump!", px[best], py[best] - tunnelMm * k, GameEffects.RED, 24);
            if (listener != null) listener.onBumpStart();
        }
        else if (inContact && distPx < maxDevPx - HYSTERESIS_MM * k) {
            endContact(t);
        }

        // The cookie follows the finger but can't pass through a wall
        if (distPx > maxDevPx && distPx > 0) {
            cookieX = px[best] + (x - px[best]) * maxDevPx / distPx;
            cookieY = py[best] + (y - py[best]) * maxDevPx / distPx;
        }
        else {
            cookieX = x;
            cookieY = y;
        }
        // Rolls as it travels
        cookieAngle = (float) Math.toDegrees(arc[best] / (cookieMm / 2));

        deviationSum += Math.abs(signedDevMm);
        deviationCount++;
        maxDeviation = Math.max(maxDeviation, Math.abs(signedDevMm));

        if (best > maxIdx) maxIdx = best;
        while (nextSprinkle < sprinkleArc.length && sprinkleArc[nextSprinkle] <= arc[maxIdx]) {
            if (inContact) {
                sprinkleState[nextSprinkle] = 2;
            }
            else {
                sprinkleState[nextSprinkle] = 1;
                int i = sprinkleIdx[nextSprinkle];
                effects.ring(px[i], py[i], 0.8f * k);
                if (listener != null) listener.onCollect();
            }
            nextSprinkle++;
        }

        logSample(x, y, elapsed, event, best, signedDevMm);
        invalidate();
    }

    private void logSample(float x, float y, long elapsed, String event, int idx, float signedDevMm) {
        log.append(String.format(Locale.US, "%d,%s,%d,%s,%.2f,%.2f,%.2f,%.2f,%d\n",
                spiral + 1, clockwise ? "CW" : "CCW", elapsed, event,
                (x - cx) / mmPx, (y - cy) / mmPx, arc[idx] * scale, signedDevMm, inContact ? 1 : 0));
    }

    private void endContact(long t) {
        if (!inContact) return;
        inContact = false;
        bumpMillis += t - contactStart;
    }

    private void escape(long elapsed) {
        escaped = true;
        dragging = false;
        cookieX = px[n - 1];
        cookieY = py[n - 1];
        effects.burst(cookieX, cookieY, tunnelMm * k);
        String stars = bumps == 0 ? "★★★" : bumps <= 2 ? "★★☆" : "★☆☆";
        effects.popup("Escaped!", cx, cy - 8 * mmPx, GameEffects.WHITE, 36);
        effects.popup(stars, cx, cy + 4 * mmPx, GameEffects.GOLD, 40);

        Result r = new Result();
        r.spiral = spiral;
        r.clockwise = clockwise;
        r.millis = elapsed;
        r.bumps = bumps;
        r.bumpMillis = bumpMillis;
        r.meanDeviationMm = deviationCount == 0 ? 0 : (float) (deviationSum / deviationCount);
        r.maxDeviationMm = maxDeviation;
        r.lifts = lifts;
        if (listener != null) listener.onSpiralEscaped(r);
    }

    private static int blend(int from, int to, float f) {
        int a = (int) (((from >>> 24) & 0xFF) * (1 - f) + ((to >>> 24) & 0xFF) * f);
        int r = (int) (((from >> 16) & 0xFF) * (1 - f) + ((to >> 16) & 0xFF) * f);
        int g = (int) (((from >> 8) & 0xFF) * (1 - f) + ((to >> 8) & 0xFF) * f);
        int b = (int) ((from & 0xFF) * (1 - f) + (to & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
