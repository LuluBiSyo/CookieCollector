package com.example.cookiecollector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Analysis of the spiral tunnel game, from every touch sample of every spiral.
 *
 * Reference values: no published norms exist for tracing a cookie through a tunnel. Digitized
 * spiral drawing (San Luciano et al. 2016, PLoS ONE; 138 PD, 150 controls) found PD spirals
 * slower (mean speed 18.6 vs 21.7 cm/s), less smooth (second-order smoothness) and more
 * variable in loop width than controls'. That study drew freely rather than tracing a tunnel,
 * so its numbers don't transfer; the values below are assumptions that follow the same
 * directions, scaled to this task. They should be replaced with values measured in this game.
 */
final class SpiralAnalysis {
    private SpiralAnalysis() {}

    // {non-PD mean, non-PD SD, PD mean, PD SD}, all assumed
    static final double[] SPEED_MM_S = {35, 12, 22, 9};
    static final double[] BUMPS_PER_SPIRAL = {0.7, 0.8, 2.5, 2.0};
    static final double[] MEAN_DEVIATION_MM = {1.2, 0.4, 1.8, 0.6};
    static final double[] DEVIATION_SD_MM = {1.0, 0.3, 1.4, 0.5};
    static final double[] SPEED_CV = {0.45, 0.15, 0.65, 0.20};

    private static final long BIN_MILLIS = 100;
    private static final long MIN_BIN_SPAN_MILLIS = 50;
    private static final int AMBER = 0xFFFFB300;
    private static final int GREY = 0xFF8FA3BF;

    /** The numbers behind the report, kept separate so they can be tested. */
    static class Summary {
        int spirals;
        float speed, bumpsPerSpiral, meanDeviation, deviationSd, speedCv;
        float score = -1;
    }

    static Summary summarize(List<SpiralTunnelView.Result> results) {
        Summary s = new Summary();
        s.spirals = results.size();
        if (results.isEmpty()) return s;
        double length = 0, seconds = 0, devSum = 0, devSqSum = 0, absDevSum = 0;
        int bumps = 0, devCount = 0;
        ArrayList<Float> binSpeeds = new ArrayList<>();
        for (SpiralTunnelView.Result r : results) {
            length += r.tunnelLengthMm;
            seconds += r.millis / 1000.0;
            bumps += r.bumps;
            for (float[] p : r.samples) {
                devSum += p[4];
                devSqSum += p[4] * p[4];
                absDevSum += Math.abs(p[4]);
                devCount++;
            }
            binSpeeds.addAll(binnedSpeeds(r.samples));
        }
        s.speed = seconds > 0 ? (float) (length / seconds) : 0;
        s.bumpsPerSpiral = (float) bumps / results.size();
        s.meanDeviation = devCount > 0 ? (float) (absDevSum / devCount) : 0;
        double meanDev = devCount > 0 ? devSum / devCount : 0;
        s.deviationSd = devCount > 1 ? (float) Math.sqrt(Math.max(0, (devSqSum - devCount * meanDev * meanDev) / (devCount - 1))) : 0;
        s.speedCv = cv(binSpeeds);

        double llr = ReportData.normalLlr(SPEED_MM_S[0], SPEED_MM_S[1], SPEED_MM_S[2], SPEED_MM_S[3], s.speed)
                + ReportData.normalLlr(BUMPS_PER_SPIRAL[0], BUMPS_PER_SPIRAL[1], BUMPS_PER_SPIRAL[2], BUMPS_PER_SPIRAL[3], s.bumpsPerSpiral)
                + ReportData.normalLlr(MEAN_DEVIATION_MM[0], MEAN_DEVIATION_MM[1], MEAN_DEVIATION_MM[2], MEAN_DEVIATION_MM[3], s.meanDeviation)
                + ReportData.normalLlr(DEVIATION_SD_MM[0], DEVIATION_SD_MM[1], DEVIATION_SD_MM[2], DEVIATION_SD_MM[3], s.deviationSd)
                + ReportData.normalLlr(SPEED_CV[0], SPEED_CV[1], SPEED_CV[2], SPEED_CV[3], s.speedCv);
        s.score = ReportData.scoreFromMeanLlr(llr, 5);
        return s;
    }

    static ReportData analyze(List<SpiralTunnelView.Result> results) {
        Summary s = summarize(results);
        ReportData d = new ReportData();
        d.title = "Your Steady Spiral report";
        d.subtitle = String.format(Locale.US, "%d %s traced", s.spirals, s.spirals == 1 ? "spiral" : "spirals");
        d.score = s.score;
        d.scoreCaption = "PD-like tracing";
        d.notEnoughData = "Play again and finish a spiral";

        d.features.add(feature("Tracing speed", String.format(Locale.US, "%.0f mm/s", s.speed), 0, Math.max(50, s.speed * 1.2f), SPEED_MM_S, s.speed));
        d.features.add(feature("Wall bumps per spiral", String.format(Locale.US, "%.1f", s.bumpsPerSpiral), 0, Math.max(4, s.bumpsPerSpiral * 1.2f), BUMPS_PER_SPIRAL, s.bumpsPerSpiral));
        d.features.add(feature("Distance from center line", String.format(Locale.US, "%.1f mm", s.meanDeviation), 0, Math.max(3, s.meanDeviation * 1.2f), MEAN_DEVIATION_MM, s.meanDeviation));
        d.features.add(feature("Side-to-side wobble", String.format(Locale.US, "%.1f mm", s.deviationSd), 0, Math.max(2.5f, s.deviationSd * 1.2f), DEVIATION_SD_MM, s.deviationSd));
        d.features.add(feature("Speed unevenness", String.format(Locale.US, "%.2f", s.speedCv), 0, Math.max(1, s.speedCv * 1.2f), SPEED_CV, s.speedCv));

        if (!results.isEmpty()) {
            // The last spiral: the tunnel, the finger's path, and where it touched a wall
            SpiralTunnelView.Result last = results.get(results.size() - 1);
            float range = 0;
            for (int i = 0; i < last.centerX.length; i++) {
                range = Math.max(range, Math.max(Math.abs(last.centerX[i]), Math.abs(last.centerY[i])));
            }
            range += last.tunnelWidthMm;
            ArrayList<Float> fx = new ArrayList<>(), fy = new ArrayList<>(), bx = new ArrayList<>(), by = new ArrayList<>();
            for (float[] p : last.samples) {
                fx.add(p[1]);
                fy.add(-p[2]);
                if (p[5] > 0) {
                    bx.add(p[1]);
                    by.add(-p[2]);
                }
            }
            float[] cy = new float[last.centerY.length];
            for (int i = 0; i < cy.length; i++) cy[i] = -last.centerY[i];
            d.leftChart = new ReportData.Chart("Your path", "amber: touching a wall", "mm", -range, range, -range, range)
                    .add(ReportData.Series.tube(ReportData.TRACK, last.centerX, cy, last.tunnelWidthMm))
                    .add(ReportData.Series.line(ReportData.WHITE, ReportData.toArray(fx), ReportData.toArray(fy)))
                    .add(ReportData.Series.points(AMBER, ReportData.toArray(bx), ReportData.toArray(by)));
            d.leftChart.equalAspect = true;

            // Signed distance from the center line along the tunnel, for all spirals
            ArrayList<Float> ax = new ArrayList<>(), dy = new ArrayList<>();
            float maxArc = 0, maxDev = last.wallContactMm * 1.6f;
            for (SpiralTunnelView.Result r : results) {
                maxArc = Math.max(maxArc, r.tunnelLengthMm);
                for (float[] p : r.samples) {
                    ax.add(p[3]);
                    dy.add(p[4]);
                    maxDev = Math.max(maxDev, Math.abs(p[4]) * 1.1f);
                }
            }
            d.rightChart = new ReportData.Chart("Off the center line", "along the tunnel (mm)", "mm", 0, Math.max(1, maxArc), -maxDev, maxDev)
                    .labels("0", String.format(Locale.US, "%.0f", maxArc), String.format(Locale.US, "-%.0f", maxDev), String.format(Locale.US, "%.0f", maxDev))
                    .add(ReportData.Series.dashedY(GREY, 0))
                    .add(ReportData.Series.dashedY(AMBER, last.wallContactMm))
                    .add(ReportData.Series.dashedY(AMBER, -last.wallContactMm))
                    .add(ReportData.Series.points(ReportData.WHITE, ReportData.toArray(ax), ReportData.toArray(dy)));
        }

        d.footer1 = "Reference values are assumptions, following the direction";
        d.footer2 = "of San Luciano et al. 2016 spiral drawing. Not a diagnosis.";
        return d;
    }

    private static ReportData.Feature feature(String label, String value, float min, float max, double[] ref, float you) {
        return new ReportData.Feature(label, value, min, max, ref[0], ref[2], you);
    }

    /** Speed along the tunnel in 100 ms bins; bins with too little time in them (e.g. a lifted finger) are skipped. */
    static ArrayList<Float> binnedSpeeds(ArrayList<float[]> samples) {
        ArrayList<Float> speeds = new ArrayList<>();
        int i = 0;
        while (i < samples.size()) {
            long bin = (long) samples.get(i)[0] / BIN_MILLIS;
            int j = i;
            while (j + 1 < samples.size() && (long) samples.get(j + 1)[0] / BIN_MILLIS == bin) j++;
            float dt = samples.get(j)[0] - samples.get(i)[0];
            if (dt >= MIN_BIN_SPAN_MILLIS) speeds.add((samples.get(j)[3] - samples.get(i)[3]) / (dt / 1000f));
            i = j + 1;
        }
        return speeds;
    }

    private static float cv(ArrayList<Float> v) {
        if (v.size() < 2) return 0;
        double sum = 0, sq = 0;
        for (float x : v) sum += x;
        double mean = sum / v.size();
        for (float x : v) sq += (x - mean) * (x - mean);
        return mean <= 0 ? 0 : (float) (Math.sqrt(sq / (v.size() - 1)) / mean);
    }
}
