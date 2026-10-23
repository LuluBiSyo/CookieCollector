package com.example.cookiecollector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * One-screen summary of the swipe game's kinematics: an overall PD-likeness gauge, the player's
 * value for each key feature placed between the published non-PD and PD group means, movement
 * time against swipe length with the paper's two fitted lines, and the average speed profile.
 */
public class SwipeReportView extends View {
    private static final int GREEN = 0xFF66BB6A;
    private static final int AMBER = 0xFFFFB300;
    private static final int RED = 0xFFEF5350;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFF8FA3BF;
    private static final int TRACK = 0xFF2E4A6B;

    private final float dp;
    private final float sp;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SwipeAnalysis.Report report;
    // Space left free at the bottom for the button
    private float bottomReserve;

    public SwipeReportView(Context context) { this(context, null); }

    public SwipeReportView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        sp = getResources().getDisplayMetrics().scaledDensity;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        bottomReserve = 76 * dp;
    }

    void setReport(SwipeAnalysis.Report report) {
        this.report = report;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (report == null) return;
        float w = getWidth();
        float pad = 16 * dp;
        float usable = getHeight() - bottomReserve;
        float y = pad;

        // Title
        y += 26 * sp;
        drawText(canvas, "Your swipe report", w / 2, y, 24, WHITE, true, Paint.Align.CENTER);
        y += 20 * sp;
        drawText(canvas, String.format(Locale.US, "%d of %d swipes analysed", report.validStrokes, report.totalStrokes),
                w / 2, y, 14, GREY, false, Paint.Align.CENTER);

        // Gauge
        float gaugeTop = y + 12 * dp;
        float gaugeR = Math.min(w * 0.30f, usable * 0.11f);
        drawGauge(canvas, w / 2, gaugeTop + gaugeR, gaugeR);
        y = gaugeTop + gaugeR + 62 * sp;

        // Legend
        float legendY = y;
        float lx = pad;
        lx = drawLegendItem(canvas, lx, legendY, GREEN, "Typical without PD");
        lx = drawLegendItem(canvas, lx + 14 * dp, legendY, RED, "Typical with PD");
        drawLegendItem(canvas, lx + 14 * dp, legendY, WHITE, "You");
        y += 10 * dp;

        // Feature bars
        float chartsHeight = usable * 0.22f;
        float footer = 34 * sp;
        float rowH = (usable - y - chartsHeight - footer - 16 * dp) / 5;
        SwipeAnalysis.Reference d = SwipeAnalysis.DURATION_S;
        SwipeAnalysis.Reference v = SwipeAnalysis.SPEED_MM_S;
        SwipeAnalysis.Reference s = SwipeAnalysis.STRAIGHTNESS;
        SwipeAnalysis.Reference b = SwipeAnalysis.MS_PER_MM;
        drawFeature(canvas, y, rowH, pad, w - pad, "Swipe duration", fmt("%.2f s", report.meanDurationS),
                0, Math.max(0.9f, report.meanDurationS * 1.2f), d.nonPdMean, d.pdMean, report.meanDurationS);
        y += rowH;
        drawFeature(canvas, y, rowH, pad, w - pad, "Average speed", fmt("%.0f mm/s", report.meanSpeed),
                0, Math.max(400, report.meanSpeed * 1.2f), v.nonPdMean, v.pdMean, report.meanSpeed);
        y += rowH;
        drawFeature(canvas, y, rowH, pad, w - pad, "Straightness", fmt("%.3f", report.meanStraightness),
                0.75f, 1f, s.nonPdMean, s.pdMean, Math.max(0.75f, report.meanStraightness));
        y += rowH;
        if (report.slopeUsed) {
            drawFeature(canvas, y, rowH, pad, w - pad, "Time per mm of swipe", fmt("%.1f ms/mm", report.slopeMsPerMm),
                    0, Math.max(12, report.slopeMsPerMm * 1.2f), b.nonPdMean, b.pdMean, Math.max(0, report.slopeMsPerMm));
        }
        else {
            drawFeature(canvas, y, rowH, pad, w - pad, "Time per mm of swipe", "needs more swipes",
                    0, 12, b.nonPdMean, b.pdMean, Float.NaN);
        }
        y += rowH;
        drawFeature(canvas, y, rowH, pad, w - pad, "Path done at half time", fmt("%.0f%%", report.meanMidShare * 100),
                25, 60, SwipeAnalysis.NON_PD_MID_SHARE * 100, SwipeAnalysis.PD_MID_SHARE * 100, report.meanMidShare * 100);
        y += rowH + 8 * dp;

        // Charts side by side
        float gap = 12 * dp;
        float cw = (w - 2 * pad - gap) / 2;
        drawTimeVsLength(canvas, new RectF(pad, y, pad + cw, y + chartsHeight));
        drawSpeedProfile(canvas, new RectF(pad + cw + gap, y, w - pad, y + chartsHeight));
        y += chartsHeight + 18 * sp;

        drawText(canvas, "Screening estimate from published group statistics", w / 2, y, 12, GREY, false, Paint.Align.CENTER);
        drawText(canvas, "(Bi, Chen & Gu). Not a medical diagnosis.", w / 2, y + 15 * sp, 12, GREY, false, Paint.Align.CENTER);
    }

    private void drawGauge(Canvas c, float cx, float cy, float r) {
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        stroke.setStrokeWidth(r * 0.2f);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setPathEffect(null);
        // Three bands: 0-35% low, 35-65% moderate, 65-100% elevated
        stroke.setColor(GREEN);
        c.drawArc(oval, 180, 180 * 0.35f, false, stroke);
        stroke.setColor(AMBER);
        c.drawArc(oval, 180 + 180 * 0.35f, 180 * 0.30f, false, stroke);
        stroke.setColor(RED);
        c.drawArc(oval, 180 + 180 * 0.65f, 180 * 0.35f, false, stroke);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        if (report.pdScore < 0) {
            drawText(c, "Not enough swipes", cx, cy - r * 0.15f, 20, WHITE, true, Paint.Align.CENTER);
            drawText(c, "Play again and slice at least 5 cookies", cx, cy + 22 * sp, 14, GREY, false, Paint.Align.CENTER);
            return;
        }
        float score = report.pdScore;
        double a = Math.PI * (1 - score);
        stroke.setColor(WHITE);
        stroke.setStrokeWidth(4 * dp);
        c.drawLine(cx, cy, cx + (float) (Math.cos(a) * r * 0.78), cy - (float) (Math.sin(a) * r * 0.78), stroke);
        fill.setColor(WHITE);
        c.drawCircle(cx, cy, 6 * dp, fill);

        int color = score < 0.35f ? GREEN : score < 0.65f ? AMBER : RED;
        String level = score < 0.35f ? "Low" : score < 0.65f ? "Moderate" : "Elevated";
        drawText(c, String.format(Locale.US, "%.0f%%", score * 100), cx, cy - r * 0.3f, 30, WHITE, true, Paint.Align.CENTER);
        drawText(c, level + " PD-like swiping", cx, cy + 26 * sp, 18, color, true, Paint.Align.CENTER);
        drawText(c, "Compared with the paper's PD and non-PD groups", cx, cy + 46 * sp, 12, GREY, false, Paint.Align.CENTER);
    }

    private float drawLegendItem(Canvas c, float x, float y, int color, String label) {
        fill.setColor(color);
        c.drawCircle(x + 5 * dp, y - 4 * sp, 5 * dp, fill);
        drawText(c, label, x + 14 * dp, y, 12, GREY, false, Paint.Align.LEFT);
        text.setTextSize(12 * sp);
        return x + 14 * dp + text.measureText(label);
    }

    /** Label and value on one line, then a track with the two group means and the player's value. */
    private void drawFeature(Canvas c, float top, float h, float left, float right, String label, String value,
                             float min, float max, double nonPd, double pd, float you) {
        float textY = top + h * 0.42f;
        drawText(c, label, left, textY, 15, WHITE, false, Paint.Align.LEFT);
        drawText(c, value, right, textY, 15, Float.isNaN(you) ? GREY : WHITE, true, Paint.Align.RIGHT);

        float ty = top + h * 0.72f;
        stroke.setPathEffect(null);
        stroke.setColor(TRACK);
        stroke.setStrokeWidth(6 * dp);
        c.drawLine(left, ty, right, ty, stroke);
        drawTick(c, pos(nonPd, min, max, left, right), ty, GREEN);
        drawTick(c, pos(pd, min, max, left, right), ty, RED);
        if (!Float.isNaN(you)) {
            float x = pos(you, min, max, left, right);
            fill.setColor(0xFF0D1B2A);
            c.drawCircle(x, ty, 8 * dp, fill);
            fill.setColor(WHITE);
            c.drawCircle(x, ty, 6 * dp, fill);
        }
    }

    private void drawTick(Canvas c, float x, float y, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(4 * dp);
        c.drawLine(x, y - 9 * dp, x, y + 9 * dp, stroke);
    }

    private static float pos(double value, float min, float max, float left, float right) {
        float f = (float) Math.max(0, Math.min(1, (value - min) / (max - min)));
        return left + f * (right - left);
    }

    /** Each swipe as a dot, with the paper's mixed-model lines MT = a + bL for each group. */
    private void drawTimeVsLength(Canvas c, RectF box) {
        float maxL = 80;
        float maxT = 1f;
        for (int i = 0; i < report.lengthsMm.length; i++) {
            maxL = Math.max(maxL, report.lengthsMm[i] * 1.1f);
            maxT = Math.max(maxT, report.durationsS[i] * 1.1f);
        }
        RectF plot = chartFrame(c, box, "Time vs swipe length", "length (mm)", "s");
        line(c, plot, 0, (float) SwipeAnalysis.NON_PD_INTERCEPT_MS / 1000, maxL,
                (float) (SwipeAnalysis.NON_PD_INTERCEPT_MS + SwipeAnalysis.MS_PER_MM.nonPdMean * maxL) / 1000, maxL, maxT, GREEN);
        line(c, plot, 0, (float) SwipeAnalysis.PD_INTERCEPT_MS / 1000, maxL,
                (float) (SwipeAnalysis.PD_INTERCEPT_MS + SwipeAnalysis.MS_PER_MM.pdMean * maxL) / 1000, maxL, maxT, RED);
        fill.setColor(WHITE);
        for (int i = 0; i < report.lengthsMm.length; i++) {
            float x = plot.left + Math.min(1, report.lengthsMm[i] / maxL) * plot.width();
            float y = plot.bottom - Math.min(1, report.durationsS[i] / maxT) * plot.height();
            c.drawCircle(x, y, 2.5f * dp, fill);
        }
        axisLabels(c, plot, "0", fmt("%.0f", maxL), fmt("%.1f", maxT));
    }

    /** The player's mean speed over normalized time, with the paper's typical peak speeds. */
    private void drawSpeedProfile(Canvas c, RectF box) {
        float maxV = 600;
        for (float v : report.meanProfile) maxV = Math.max(maxV, v * 1.1f);
        RectF plot = chartFrame(c, box, "Speed profile", "time (start to end)", "mm/s");
        dashed(c, plot, (float) SwipeAnalysis.NON_PD_PEAK_SPEED / maxV, GREEN);
        dashed(c, plot, (float) SwipeAnalysis.PD_PEAK_SPEED / maxV, RED);
        if (report.validStrokes > 0) {
            Path p = new Path();
            float[] v = report.meanProfile;
            for (int j = 0; j < v.length; j++) {
                float x = plot.left + plot.width() * j / (v.length - 1);
                float y = plot.bottom - Math.min(1, v[j] / maxV) * plot.height();
                if (j == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            stroke.setPathEffect(null);
            stroke.setColor(WHITE);
            stroke.setStrokeWidth(2.5f * dp);
            c.drawPath(p, stroke);
        }
        axisLabels(c, plot, "0", "1", fmt("%.0f", maxV));
    }

    private RectF chartFrame(Canvas c, RectF box, String title, String xLabel, String yUnit) {
        drawText(c, title, box.left, box.top + 13 * sp, 13, WHITE, true, Paint.Align.LEFT);
        drawText(c, yUnit, box.right, box.top + 13 * sp, 11, GREY, false, Paint.Align.RIGHT);
        RectF plot = new RectF(box.left + 26 * dp, box.top + 22 * sp, box.right - 4 * dp, box.bottom - 30 * sp);
        stroke.setPathEffect(null);
        stroke.setColor(TRACK);
        stroke.setStrokeWidth(1.5f * dp);
        c.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, stroke);
        c.drawLine(plot.left, plot.top, plot.left, plot.bottom, stroke);
        drawText(c, xLabel, (plot.left + plot.right) / 2, box.bottom - 4 * sp, 11, GREY, false, Paint.Align.CENTER);
        return plot;
    }

    private void axisLabels(Canvas c, RectF plot, String x0, String x1, String yMax) {
        drawText(c, x0, plot.left, plot.bottom + 13 * sp, 10, GREY, false, Paint.Align.CENTER);
        drawText(c, x1, plot.right, plot.bottom + 13 * sp, 10, GREY, false, Paint.Align.RIGHT);
        drawText(c, yMax, plot.left - 4 * dp, plot.top + 8 * sp, 10, GREY, false, Paint.Align.RIGHT);
    }

    private void line(Canvas c, RectF plot, float x0, float y0, float x1, float y1, float maxX, float maxY, int color) {
        stroke.setPathEffect(null);
        stroke.setColor(color);
        stroke.setStrokeWidth(2.5f * dp);
        // Clip the far end to the top of the chart
        if (y1 > maxY) {
            x1 = x0 + (x1 - x0) * (maxY - y0) / (y1 - y0);
            y1 = maxY;
        }
        c.drawLine(plot.left + x0 / maxX * plot.width(), plot.bottom - y0 / maxY * plot.height(),
                plot.left + x1 / maxX * plot.width(), plot.bottom - y1 / maxY * plot.height(), stroke);
    }

    private void dashed(Canvas c, RectF plot, float fraction, int color) {
        float y = plot.bottom - Math.min(1, fraction) * plot.height();
        stroke.setColor(color);
        stroke.setStrokeWidth(2 * dp);
        stroke.setPathEffect(new DashPathEffect(new float[]{6 * dp, 5 * dp}, 0));
        c.drawLine(plot.left, y, plot.right, y, stroke);
        stroke.setPathEffect(null);
    }

    private void drawText(Canvas c, String s, float x, float y, float sizeSp, int color, boolean bold, Paint.Align align) {
        text.setTextSize(sizeSp * sp);
        text.setColor(color);
        text.setFakeBoldText(bold);
        text.setTextAlign(align);
        c.drawText(s, x, y, text);
    }

    private static String fmt(String f, float v) { return String.format(Locale.US, f, v); }
}
