package com.example.cookiecollector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Transparent overlay for the tap game. It draws a soft glow behind the current cookie
 * and the hit/miss effects, and never takes touches, so taps still reach TapGameActivity.
 */
public class EffectsOverlayView extends View {
    private final GameEffects effects;
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean haloShown = false;
    private float haloX, haloY, haloR;
    private long lastFrameTime = -1;

    public EffectsOverlayView(Context context) { this(context, null); }

    public EffectsOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        effects = new GameEffects(context);
    }

    GameEffects getEffects() {
        kick();
        return effects;
    }

    /** Positions are in screen pixels and converted to this view's pixels. */
    void showHalo(float screenX, float screenY, float radius) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        haloX = screenX - loc[0];
        haloY = screenY - loc[1];
        haloR = radius;
        haloShown = true;
        kick();
    }

    void hideHalo() {
        haloShown = false;
        invalidate();
    }

    float toViewX(float screenX) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        return screenX - loc[0];
    }

    float toViewY(float screenY) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        return screenY - loc[1];
    }

    private void kick() {
        if (lastFrameTime < 0) lastFrameTime = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        long dt = lastFrameTime < 0 ? 0 : Math.min(now - lastFrameTime, 50);
        lastFrameTime = now;
        effects.update(dt);

        if (haloShown) {
            // Gentle pulse so the cookie stands out on the dark background
            float pulse = 0.85f + 0.15f * (float) Math.sin(now / 250.0);
            float r = haloR * 1.7f * pulse;
            // Clear over the cookie itself (this view sits on top of it), glowing just outside its edge
            float edge = Math.min(0.95f, haloR / r);
            haloPaint.setShader(new RadialGradient(haloX, haloY, r,
                    new int[]{0x00FFD54F, 0x00FFD54F, 0x90FFD54F, 0x00FFD54F},
                    new float[]{0f, edge * 0.97f, edge, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(haloX, haloY, r, haloPaint);
        }
        effects.draw(canvas);

        if (haloShown || !effects.isEmpty()) postInvalidateOnAnimation();
        else lastFrameTime = -1;
    }
}
