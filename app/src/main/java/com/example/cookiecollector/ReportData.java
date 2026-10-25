package com.example.cookiecollector;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * What a game's end-of-game report shows: an overall PD-likeness score, a few features each
 * placed between a non-PD and a PD reference value, and two small charts. Built by each game's
 * analysis class and drawn by {@link GameReportView}.
 */
class ReportData implements Serializable {
    static final int GREEN = 0xFF66BB6A;
    static final int RED = 0xFFEF5350;
    static final int WHITE = 0xFFFFFFFF;
    static final int TRACK = 0xFF2E4A6B;

    String title;
    String subtitle;
    // 0..1, or -1 when there isn't enough data
    float score = -1;
    String scoreCaption;       // e.g. "PD-like swiping"
    String notEnoughData;      // shown in the gauge when score < 0
    final ArrayList<Feature> features = new ArrayList<>();
    Chart leftChart;
    Chart rightChart;
    String footer1;
    String footer2;

    static class Feature implements Serializable {
        final String label;
        final String value;
        final float min, max;
        final double nonPd, pd;
        final float you;           // NaN when not available

        Feature(String label, String value, float min, float max, double nonPd, double pd, float you) {
            this.label = label;
            this.value = value;
            this.min = min;
            this.max = max;
            this.nonPd = nonPd;
            this.pd = pd;
            this.you = you;
        }
    }

    static class Chart implements Serializable {
        final String title, xLabel, yUnit;
        final float xMin, xMax, yMin, yMax;
        // Same scale on both axes, for drawings in mm such as a traced path
        boolean equalAspect;
        String xMinLabel, xMaxLabel, yMinLabel, yMaxLabel;
        final ArrayList<Series> series = new ArrayList<>();

        Chart(String title, String xLabel, String yUnit, float xMin, float xMax, float yMin, float yMax) {
            this.title = title;
            this.xLabel = xLabel;
            this.yUnit = yUnit;
            this.xMin = xMin;
            this.xMax = xMax;
            this.yMin = yMin;
            this.yMax = yMax;
        }

        Chart labels(String xMinLabel, String xMaxLabel, String yMinLabel, String yMaxLabel) {
            this.xMinLabel = xMinLabel;
            this.xMaxLabel = xMaxLabel;
            this.yMinLabel = yMinLabel;
            this.yMaxLabel = yMaxLabel;
            return this;
        }

        Chart add(Series s) {
            series.add(s);
            return this;
        }
    }

    static class Series implements Serializable {
        enum Kind { POINTS, LINE, DASHED_Y, CIRCLE, TUBE }

        final Kind kind;
        final int color;
        final float[] xs, ys;
        // DASHED_Y: the y value; CIRCLE: radius; TUBE: width; all in data units
        final float value;

        private Series(Kind kind, int color, float[] xs, float[] ys, float value) {
            this.kind = kind;
            this.color = color;
            this.xs = xs;
            this.ys = ys;
            this.value = value;
        }

        static Series points(int color, float[] xs, float[] ys) { return new Series(Kind.POINTS, color, xs, ys, 0); }
        static Series line(int color, float[] xs, float[] ys) { return new Series(Kind.LINE, color, xs, ys, 0); }
        static Series dashedY(int color, float y) { return new Series(Kind.DASHED_Y, color, null, null, y); }
        static Series circle(int color, float x, float y, float r) { return new Series(Kind.CIRCLE, color, new float[]{x}, new float[]{y}, r); }
        static Series tube(int color, float[] xs, float[] ys, float width) { return new Series(Kind.TUBE, color, xs, ys, width); }
    }

    /** log p_PD(x) - log p_nonPD(x) for normal groups, clipped so no single feature decides the score. */
    static double normalLlr(double m0, double sd0, double m1, double sd1, double x) {
        double z0 = (x - m0) / sd0;
        double z1 = (x - m1) / sd1;
        return clip(Math.log(sd0 / sd1) - z1 * z1 / 2 + z0 * z0 / 2);
    }

    static double clip(double llr) { return Math.max(-3, Math.min(3, llr)); }

    /** Equal prior odds; the features are correlated, so their evidence is averaged rather than added. */
    static float scoreFromMeanLlr(double sumLlr, int count) {
        return (float) (1 / (1 + Math.exp(-sumLlr / count)));
    }

    static float[] toArray(ArrayList<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }
}
