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
 * One-screen end-of-game report, shared by all games: an overall PD-likeness gauge, the player's
 * value for each feature placed between the non-PD and PD reference values, and two charts.
 * What is shown comes from a {@link ReportData}.
 */
public class GameReportView extends View {
    private static final int AMBER = 0xFFFFB300;
    private static final int GREY = 0xFF8FA3BF;
    private static final int BACKGROUND = 0xFF0D1B2A;

    private final float dp;
    private final float sp;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ReportData report;
    // Space left free at the bottom for the button
    private final float bottomReserve;

    public GameReportView(Context context) { this(context, null); }

    public GameReportView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        sp = getResources().getDisplayMetrics().scaledDensity;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        bottomReserve = 76 * dp;
    }

    void setReport(ReportData report) {
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

        y += 26 * sp;
        drawText(canvas, report.title, w / 2, y, 24, ReportData.WHITE, true, Paint.Align.CENTER);
        y += 20 * sp;
        drawText(canvas, report.subtitle, w / 2, y, 14, GREY, false, Paint.Align.CENTER);

        float gaugeTop = y + 12 * dp;
        float gaugeR = Math.min(w * 0.30f, usable * 0.11f);
        drawGauge(canvas, w / 2, gaugeTop + gaugeR, gaugeR);
        y = gaugeTop + gaugeR + 62 * sp;

        float lx = pad;
        lx = drawLegendItem(canvas, lx, y, ReportData.GREEN, "Typical without PD");
        lx = drawLegendItem(canvas, lx + 14 * dp, y, ReportData.RED, "Typical with PD");
        drawLegendItem(canvas, lx + 14 * dp, y, ReportData.WHITE, "You");
        y += 10 * dp;

        float chartsHeight = usable * 0.22f;
        float footer = 34 * sp;
        int rows = Math.max(1, report.features.size());
        float rowH = Math.min(64 * dp, (usable - y - chartsHeight - footer - 16 * dp) / rows);
        for (ReportData.Feature f : report.features) {
            drawFeature(canvas, y, rowH, pad, w - pad, f);
            y += rowH;
        }
        y += 8 * dp;

        float gap = 12 * dp;
        float cw = (w - 2 * pad - gap) / 2;
        if (report.leftChart != null) drawChart(canvas, new RectF(pad, y, pad + cw, y + chartsHeight), report.leftChart);
        if (report.rightChart != null) drawChart(canvas, new RectF(pad + cw + gap, y, w - pad, y + chartsHeight), report.rightChart);
        y += chartsHeight + 18 * sp;

        if (report.footer1 != null) drawText(canvas, report.footer1, w / 2, y, 12, GREY, false, Paint.Align.CENTER);
        if (report.footer2 != null) drawText(canvas, report.footer2, w / 2, y + 15 * sp, 12, GREY, false, Paint.Align.CENTER);
    }

    private void drawGauge(Canvas c, float cx, float cy, float r) {
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        stroke.setStrokeWidth(r * 0.2f);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setPathEffect(null);
        // Three bands: 0-35% low, 35-65% moderate, 65-100% elevated
        stroke.setColor(ReportData.GREEN);
        c.drawArc(oval, 180, 180 * 0.35f, false, stroke);
        stroke.setColor(AMBER);
        c.drawArc(oval, 180 + 180 * 0.35f, 180 * 0.30f, false, stroke);
        stroke.setColor(ReportData.RED);
        c.drawArc(oval, 180 + 180 * 0.65f, 180 * 0.35f, false, stroke);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        if (report.score < 0) {
            drawText(c, "Not enough data", cx, cy - r * 0.15f, 20, ReportData.WHITE, true, Paint.Align.CENTER);
            if (report.notEnoughData != null) drawText(c, report.notEnoughData, cx, cy + 22 * sp, 14, GREY, false, Paint.Align.CENTER);
            return;
        }
        float score = report.score;
        double a = Math.PI * (1 - score);
        stroke.setColor(ReportData.WHITE);
        stroke.setStrokeWidth(4 * dp);
        c.drawLine(cx, cy, cx + (float) (Math.cos(a) * r * 0.78), cy - (float) (Math.sin(a) * r * 0.78), stroke);
        fill.setColor(ReportData.WHITE);
        c.drawCircle(cx, cy, 6 * dp, fill);

        int color = score < 0.35f ? ReportData.GREEN : score < 0.65f ? AMBER : ReportData.RED;
        String level = score < 0.35f ? "Low" : score < 0.65f ? "Moderate" : "Elevated";
        drawText(c, String.format(Locale.US, "%.0f%%", score * 100), cx, cy - r * 0.3f, 30, ReportData.WHITE, true, Paint.Align.CENTER);
        drawText(c, level + " " + report.scoreCaption, cx, cy + 26 * sp, 18, color, true, Paint.Align.CENTER);
        drawText(c, "Compared with PD and non-PD reference groups", cx, cy + 46 * sp, 12, GREY, false, Paint.Align.CENTER);
    }

    private float drawLegendItem(Canvas c, float x, float y, int color, String label) {
        fill.setColor(color);
        c.drawCircle(x + 5 * dp, y - 4 * sp, 5 * dp, fill);
        drawText(c, label, x + 14 * dp, y, 12, GREY, false, Paint.Align.LEFT);
        text.setTextSize(12 * sp);
        return x + 14 * dp + text.measureText(label);
    }

    /** Label and value on one line, then a track with the two reference values and the player's value. */
    private void drawFeature(Canvas c, float top, float h, float left, float right, ReportData.Feature f) {
        float textY = top + h * 0.42f;
        drawText(c, f.label, left, textY, 15, ReportData.WHITE, false, Paint.Align.LEFT);
        drawText(c, f.value, right, textY, 15, Float.isNaN(f.you) ? GREY : ReportData.WHITE, true, Paint.Align.RIGHT);

        float ty = top + h * 0.72f;
        stroke.setPathEffect(null);
        stroke.setColor(ReportData.TRACK);
        stroke.setStrokeWidth(6 * dp);
        c.drawLine(left, ty, right, ty, stroke);
        drawTick(c, pos(f.nonPd, f.min, f.max, left, right), ty, ReportData.GREEN);
        drawTick(c, pos(f.pd, f.min, f.max, left, right), ty, ReportData.RED);
        if (!Float.isNaN(f.you)) {
            float x = pos(f.you, f.min, f.max, left, right);
            fill.setColor(BACKGROUND);
            c.drawCircle(x, ty, 8 * dp, fill);
            fill.setColor(ReportData.WHITE);
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

    private void drawChart(Canvas c, RectF box, ReportData.Chart chart) {
        drawText(c, chart.title, box.left, box.top + 13 * sp, 13, ReportData.WHITE, true, Paint.Align.LEFT);
        drawText(c, chart.yUnit, box.right, box.top + 13 * sp, 11, GREY, false, Paint.Align.RIGHT);
        RectF plot = new RectF(box.left + 26 * dp, box.top + 22 * sp, box.right - 4 * dp, box.bottom - 30 * sp);
        if (chart.equalAspect) {
            // Shrink one side so a millimetre is the same length on both axes
            float sx = plot.width() / (chart.xMax - chart.xMin);
            float sy = plot.height() / (chart.yMax - chart.yMin);
            float s = Math.min(sx, sy);
            float cx = plot.centerX(), cy = plot.centerY();
            float hw = (chart.xMax - chart.xMin) * s / 2, hh = (chart.yMax - chart.yMin) * s / 2;
            plot = new RectF(cx - hw, cy - hh, cx + hw, cy + hh);
        }
        else {
            stroke.setPathEffect(null);
            stroke.setColor(ReportData.TRACK);
            stroke.setStrokeWidth(1.5f * dp);
            c.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, stroke);
            c.drawLine(plot.left, plot.top, plot.left, plot.bottom, stroke);
        }
        drawText(c, chart.xLabel, (box.left + 26 * dp + box.right) / 2, box.bottom - 4 * sp, 11, GREY, false, Paint.Align.CENTER);

        c.save();
        c.clipRect(plot.left - 4 * dp, plot.top - 4 * dp, plot.right + 4 * dp, plot.bottom + 4 * dp);
        for (ReportData.Series s : chart.series) drawSeries(c, plot, chart, s);
        c.restore();

        if (chart.xMinLabel != null) drawText(c, chart.xMinLabel, plot.left, plot.bottom + 13 * sp, 10, GREY, false, Paint.Align.CENTER);
        if (chart.xMaxLabel != null) drawText(c, chart.xMaxLabel, plot.right, plot.bottom + 13 * sp, 10, GREY, false, Paint.Align.RIGHT);
        if (chart.yMaxLabel != null) drawText(c, chart.yMaxLabel, plot.left - 4 * dp, plot.top + 8 * sp, 10, GREY, false, Paint.Align.RIGHT);
        if (chart.yMinLabel != null) drawText(c, chart.yMinLabel, plot.left - 4 * dp, plot.bottom, 10, GREY, false, Paint.Align.RIGHT);
    }

    private void drawSeries(Canvas c, RectF plot, ReportData.Chart ch, ReportData.Series s) {
        float sx = plot.width() / (ch.xMax - ch.xMin);
        float sy = plot.height() / (ch.yMax - ch.yMin);
        stroke.setPathEffect(null);
        stroke.setColor(s.color);
        fill.setColor(s.color);
        switch (s.kind) {
            case POINTS:
                for (int i = 0; i < s.xs.length; i++) {
                    c.drawCircle(px(plot, ch, sx, s.xs[i]), py(plot, ch, sy, s.ys[i]), 2.5f * dp, fill);
                }
                break;
            case LINE:
            case TUBE:
                if (s.xs.length < 2) break;
                Path p = new Path();
                for (int i = 0; i < s.xs.length; i++) {
                    float x = px(plot, ch, sx, s.xs[i]);
                    float y = py(plot, ch, sy, s.ys[i]);
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                stroke.setStrokeWidth(s.kind == ReportData.Series.Kind.TUBE ? Math.max(1, s.value * sx) : 2.5f * dp);
                c.drawPath(p, stroke);
                break;
            case DASHED_Y:
                float y = py(plot, ch, sy, s.value);
                stroke.setStrokeWidth(2 * dp);
                stroke.setPathEffect(new DashPathEffect(new float[]{6 * dp, 5 * dp}, 0));
                c.drawLine(plot.left, y, plot.right, y, stroke);
                stroke.setPathEffect(null);
                break;
            case CIRCLE:
                stroke.setStrokeWidth(2 * dp);
                c.drawCircle(px(plot, ch, sx, s.xs[0]), py(plot, ch, sy, s.ys[0]), s.value * sx, stroke);
                break;
        }
    }

    private static float px(RectF plot, ReportData.Chart ch, float sx, float x) { return plot.left + (x - ch.xMin) * sx; }
    private static float py(RectF plot, ReportData.Chart ch, float sy, float y) { return plot.bottom - (y - ch.yMin) * sy; }

    private void drawText(Canvas c, String s, float x, float y, float sizeSp, int color, boolean bold, Paint.Align align) {
        if (s == null) return;
        text.setTextSize(sizeSp * sp);
        text.setColor(color);
        text.setFakeBoldText(bold);
        text.setTextAlign(align);
        c.drawText(s, x, y, text);
    }
}
