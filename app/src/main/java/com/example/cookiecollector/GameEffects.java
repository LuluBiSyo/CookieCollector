package com.example.cookiecollector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * Particle effects shared by both games: crumb bursts, an expanding shockwave ring,
 * and floating text such as "+1", "Miss" or "5 in a row!". Coordinates are in the
 * pixels of whatever view draws them.
 */
class GameEffects {
    static final int GOLD = 0xFFFFD54F;
    static final int RED = 0xFFFF6B6B;
    static final int WHITE = 0xFFFFFFFF;

    private static final long CRUMB_MILLIS = 700;
    private static final long RING_MILLIS = 400;
    private static final long POPUP_MILLIS = 900;
    private static final float GRAVITY = 0.0025f; // px per ms^2

    private static class Crumb {
        float x, y, vx, vy, size;
        int color;
        long age;
    }

    private static class Ring {
        float x, y, r;
        long age;
    }

    private static class Popup {
        String text;
        float x, y, textSize;
        int color;
        long age;
    }

    private final ArrayList<Crumb> crumbs = new ArrayList<>();
    private final ArrayList<Ring> rings = new ArrayList<>();
    private final ArrayList<Popup> popups = new ArrayList<>();
    private final Paint crumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float density;
    private final float scaledDensity;

    GameEffects(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        scaledDensity = c.getResources().getDisplayMetrics().scaledDensity;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(GOLD);
        popupPaint.setTextAlign(Paint.Align.CENTER);
        popupPaint.setFakeBoldText(true);
        popupPaint.setShadowLayer(4 * density, 0, 2 * density, 0x80000000);
    }

    /** Crumbs flying out of a broken cookie plus a golden shockwave ring. */
    void burst(float x, float y, float radius) {
        Ring ring = new Ring();
        ring.x = x;
        ring.y = y;
        ring.r = radius;
        rings.add(ring);
        int[] colors = {0xFFC68642, 0xFF8D5524, 0xFFE0AC69, 0xFF5C3A1E};
        for (int i = 0; i < 14; i++) {
            Crumb cr = new Crumb();
            double a = random.nextDouble() * 2 * Math.PI;
            float s = (0.06f + random.nextFloat() * 0.22f) * density;
            cr.x = x + (float) Math.cos(a) * radius * 0.5f;
            cr.y = y + (float) Math.sin(a) * radius * 0.5f;
            cr.vx = (float) Math.cos(a) * s;
            cr.vy = (float) Math.sin(a) * s - 0.12f * density;
            cr.size = (1.5f + random.nextFloat() * 2.5f) * density;
            cr.color = colors[random.nextInt(colors.length)];
            crumbs.add(cr);
        }
    }

    /**
     * Effects for a collected cookie. Every 5 hits in a row also gets a bigger
     * "N in a row!" banner; returns true when that happens.
     */
    boolean hit(float x, float y, float radius, int streak) {
        burst(x, y, radius);
        popup("+1", x, y - radius, GOLD, 28);
        if (streak > 0 && streak % 5 == 0) {
            popup(streak + " in a row!", x, y - radius - 40 * density, WHITE, 34);
            return true;
        }
        return false;
    }

    void popup(String text, float x, float y, int color, float textSizeSp) {
        Popup p = new Popup();
        p.text = text;
        p.x = x;
        p.y = y;
        p.color = color;
        p.textSize = textSizeSp * scaledDensity;
        popups.add(p);
    }

    boolean isEmpty() { return crumbs.isEmpty() && rings.isEmpty() && popups.isEmpty(); }

    void update(long dt) {
        Iterator<Crumb> ci = crumbs.iterator();
        while (ci.hasNext()) {
            Crumb c = ci.next();
            c.age += dt;
            c.vy += GRAVITY * dt;
            c.x += c.vx * dt;
            c.y += c.vy * dt;
            if (c.age > CRUMB_MILLIS) ci.remove();
        }
        Iterator<Ring> ri = rings.iterator();
        while (ri.hasNext()) if ((ri.next().age += dt) > RING_MILLIS) ri.remove();
        Iterator<Popup> pi = popups.iterator();
        while (pi.hasNext()) {
            Popup p = pi.next();
            p.age += dt;
            p.y -= 0.06f * density * dt;
            if (p.age > POPUP_MILLIS) pi.remove();
        }
    }

    void draw(Canvas canvas) {
        for (Ring ring : rings) {
            float f = (float) ring.age / RING_MILLIS;
            ringPaint.setStrokeWidth(4 * density * (1 - f));
            ringPaint.setAlpha((int) (220 * (1 - f)));
            canvas.drawCircle(ring.x, ring.y, ring.r * (1 + 1.2f * f), ringPaint);
        }
        for (Crumb c : crumbs) {
            crumbPaint.setColor(c.color);
            crumbPaint.setAlpha((int) (255 * (1 - (float) c.age / CRUMB_MILLIS)));
            canvas.drawCircle(c.x, c.y, c.size, crumbPaint);
        }
        for (Popup p : popups) {
            float f = (float) p.age / POPUP_MILLIS;
            // Pops in slightly larger, then settles
            float scale = f < 0.15f ? 1.3f - 2 * f : 1f;
            popupPaint.setTextSize(p.textSize * scale);
            popupPaint.setColor(p.color);
            popupPaint.setAlpha((int) (255 * Math.min(1, 2 * (1 - f))));
            float half = popupPaint.measureText(p.text) / 2;
            float x = Math.max(half, Math.min(canvas.getWidth() - half, p.x));
            canvas.drawText(p.text, x, p.y, popupPaint);
        }
    }
}
