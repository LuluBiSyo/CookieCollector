package com.example.cookiecollector;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Analysis of the tap game. Each trial's first touch gives its movement time (from the cookie
 * appearing to the touch), whether it hit, and where it landed relative to the cookie's center.
 *
 * Reference values:
 * <ul>
 *   <li>Movement time, its spread, and time per bit: this app's Finger-Fitts models for PD and
 *       non-PD movement ({@link ParkinsonsProbabilityCalculator}), evaluated at the player's
 *       distances and target size. The overall score is that model's PD probability, averaged
 *       over touches, as the tap game reported before.</li>
 *   <li>Hit rate: De Vleeschhauwer et al. 2021 (Mov Disord Clin Pract), two-target touchscreen
 *       tapping, correct taps 92.8% for controls and 88.9% for PD off medication (medians).</li>
 *   <li>Tap offset from the target center: no published value was found; 2.0 mm (non-PD) and
 *       3.0 mm (PD) are assumptions.</li>
 * </ul>
 */
final class TapAnalysis {
    private TapAnalysis() {}

    static final double NON_PD_HIT_RATE = 92.8, PD_HIT_RATE = 88.9;
    static final double NON_PD_OFFSET_MM = 2.0, PD_OFFSET_MM = 3.0;
    private static final int MIN_TRIALS_FOR_SCORE = 5;
    private static final int MIN_HITS_FOR_SLOPE = 8;
    private static final float MIN_ID_RANGE_FOR_SLOPE = 0.5f;
    private static final int AMBER = 0xFFFFB300;
    private static final int GREY = 0xFF8FA3BF;

    static ReportData analyze(UserAndStudyInfo info) {
        float width = info.getDiameter();
        ArrayList<Float> hitD = new ArrayList<>(), hitMt = new ArrayList<>(), missD = new ArrayList<>(), missMt = new ArrayList<>();
        ArrayList<Float> hitDx = new ArrayList<>(), hitDy = new ArrayList<>(), missDx = new ArrayList<>(), missDy = new ArrayList<>();
        double probabilitySum = 0, offsetSum = 0;
        int probabilityCount = 0, trials = 0, hits = 0, measuredTrials = 0;

        float[] prev = {-1, -1};
        for (int i = 0; i < info.getTotalTrials(); i++) {
            float[] target = info.getCorrectCoordinates(i);
            float dist = prev[0] == -1 ? 0 : (float) Math.hypot(prev[0] - target[0], prev[1] - target[1]);
            prev = target;
            ArrayList<UserAndStudyInfo.Touchpoint> touches = info.getTouchpoints(i);
            trials++;
            if (touches.isEmpty()) continue; // timed out: counts as a miss
            UserAndStudyInfo.Touchpoint first = touches.get(0);
            if (first.correct) hits++;
            float dx = first.x - target[0];
            float dy = first.y - target[1];
            offsetSum += Math.hypot(dx, dy);
            (first.correct ? hitDx : missDx).add(dx);
            // Screen y points down; flip it so "up" is up in the chart
            (first.correct ? hitDy : missDy).add(-dy);

            // The first trial has no previous target, so no movement distance
            if (dist <= 0) continue;
            measuredTrials++;
            (first.correct ? hitD : missD).add(dist);
            (first.correct ? hitMt : missMt).add(first.milliseconds / 1000f);
            // Same averaging as the tap game's original risk score
            for (UserAndStudyInfo.Touchpoint t : touches) {
                float p = ParkinsonsProbabilityCalculator.calculateParkinsonProbability(dist, width, t.milliseconds);
                if (p > 0) {
                    probabilitySum += p;
                    probabilityCount++;
                }
            }
        }

        // Movement time statistics over hits
        int n = hitMt.size();
        double meanD = 0, meanMt = 0, sqMt = 0;
        for (int i = 0; i < n; i++) {
            meanD += hitD.get(i) / n;
            meanMt += hitMt.get(i) * 1000 / n;
        }
        for (int i = 0; i < n; i++) sqMt += Math.pow(hitMt.get(i) * 1000 - meanMt, 2);
        float sdMt = n > 1 ? (float) Math.sqrt(sqMt / (n - 1)) : Float.NaN;

        // Time per bit: least squares of MT on Finger-Fitts index of difficulty
        float slope = Float.NaN;
        float minId = Float.MAX_VALUE, maxId = 0;
        if (n >= 2) {
            double mId = 0;
            float[] ids = new float[n];
            for (int i = 0; i < n; i++) {
                ids[i] = ParkinsonsProbabilityCalculator.indexOfDifficulty(hitD.get(i), width);
                mId += ids[i] / n;
                minId = Math.min(minId, ids[i]);
                maxId = Math.max(maxId, ids[i]);
            }
            double sxy = 0, sxx = 0;
            for (int i = 0; i < n; i++) {
                sxy += (ids[i] - mId) * (hitMt.get(i) * 1000 - meanMt);
                sxx += (ids[i] - mId) * (ids[i] - mId);
            }
            if (sxx > 0) slope = (float) (sxy / sxx);
        }
        boolean slopeUsed = n >= MIN_HITS_FOR_SLOPE && maxId - minId >= MIN_ID_RANGE_FOR_SLOPE && !Float.isNaN(slope);

        float refD = n > 0 ? (float) meanD : 30;
        float hitRate = trials == 0 ? 0 : 100f * hits / trials;
        int touched = hitDx.size() + missDx.size();
        float meanOffset = touched == 0 ? Float.NaN : (float) (offsetSum / touched);

        ReportData d = new ReportData();
        d.title = "Your Quick Tap report";
        d.subtitle = String.format(Locale.US, "%d cookies, %d hit on the first touch", trials, hits);
        d.score = measuredTrials >= MIN_TRIALS_FOR_SCORE && probabilityCount > 0 ? (float) (probabilitySum / probabilityCount) : -1;
        d.scoreCaption = "PD-like tapping";
        d.notEnoughData = "Play again and tap at least 5 cookies";

        float nonPdMt = ParkinsonsProbabilityCalculator.predictedMeanMt(false, refD, width);
        float pdMt = ParkinsonsProbabilityCalculator.predictedMeanMt(true, refD, width);
        d.features.add(new ReportData.Feature("Time to tap", n > 0 ? String.format(Locale.US, "%.0f ms", meanMt) : "no hits",
                0, Math.max(pdMt * 1.3f, (float) meanMt * 1.2f), nonPdMt, pdMt, n > 0 ? (float) meanMt : Float.NaN));
        float nonPdSd = ParkinsonsProbabilityCalculator.predictedSdMt(false, refD, width);
        float pdSd = ParkinsonsProbabilityCalculator.predictedSdMt(true, refD, width);
        d.features.add(new ReportData.Feature("Variability of tap time", Float.isNaN(sdMt) ? "needs more taps" : String.format(Locale.US, "%.0f ms", sdMt),
                0, Math.max(pdSd * 1.3f, Float.isNaN(sdMt) ? 0 : sdMt * 1.2f), nonPdSd, pdSd, sdMt));
        float nonPdB = ParkinsonsProbabilityCalculator.nonPdSlope();
        float pdB = ParkinsonsProbabilityCalculator.pdSlope();
        d.features.add(new ReportData.Feature("Time per bit of difficulty", slopeUsed ? String.format(Locale.US, "%.0f ms/bit", slope) : "needs more taps",
                0, Math.max(pdB * 1.4f, slopeUsed ? slope * 1.2f : 0), nonPdB, pdB, slopeUsed ? Math.max(0, slope) : Float.NaN));
        d.features.add(new ReportData.Feature("Hit on first touch", String.format(Locale.US, "%.0f%%", hitRate),
                70, 100, NON_PD_HIT_RATE, PD_HIT_RATE, Math.max(70, hitRate)));
        d.features.add(new ReportData.Feature("Distance from cookie center", Float.isNaN(meanOffset) ? "no taps" : String.format(Locale.US, "%.1f mm", meanOffset),
                0, Math.max(5, Float.isNaN(meanOffset) ? 0 : meanOffset * 1.2f), NON_PD_OFFSET_MM, PD_OFFSET_MM, meanOffset));

        // Time against distance, with each model's prediction for this target size
        float maxD = 40, maxT = 1.5f;
        for (float v : hitD) maxD = Math.max(maxD, v * 1.1f);
        for (float v : missD) maxD = Math.max(maxD, v * 1.1f);
        for (float v : hitMt) maxT = Math.max(maxT, v * 1.1f);
        for (float v : missMt) maxT = Math.max(maxT, v * 1.1f);
        int steps = 24;
        float[] ds = new float[steps], nonPdT = new float[steps], pdT = new float[steps];
        for (int i = 0; i < steps; i++) {
            ds[i] = 1 + (maxD - 1) * i / (steps - 1);
            nonPdT[i] = ParkinsonsProbabilityCalculator.predictedMeanMt(false, ds[i], width) / 1000;
            pdT[i] = ParkinsonsProbabilityCalculator.predictedMeanMt(true, ds[i], width) / 1000;
        }
        d.leftChart = new ReportData.Chart("Time vs distance", "distance (mm)", "s", 0, maxD, 0, maxT)
                .labels("0", String.format(Locale.US, "%.0f", maxD), null, String.format(Locale.US, "%.1f", maxT))
                .add(ReportData.Series.line(ReportData.GREEN, ds, nonPdT))
                .add(ReportData.Series.line(ReportData.RED, ds, pdT))
                .add(ReportData.Series.points(AMBER, ReportData.toArray(missD), ReportData.toArray(missMt)))
                .add(ReportData.Series.points(ReportData.WHITE, ReportData.toArray(hitD), ReportData.toArray(hitMt)));

        // Where each first touch landed, relative to the cookie (circle); misses in amber
        float radius = info.getRadius();
        float range = radius * 1.6f;
        for (float v : missDx) range = Math.max(range, Math.abs(v) * 1.1f);
        for (float v : missDy) range = Math.max(range, Math.abs(v) * 1.1f);
        d.rightChart = new ReportData.Chart("Where you tapped", "white: hit, amber: miss", "mm", -range, range, -range, range)
                .labels(null, null, null, null)
                .add(ReportData.Series.circle(GREY, 0, 0, radius))
                .add(ReportData.Series.points(AMBER, ReportData.toArray(missDx), ReportData.toArray(missDy)))
                .add(ReportData.Series.points(ReportData.WHITE, ReportData.toArray(hitDx), ReportData.toArray(hitDy)));
        d.rightChart.equalAspect = true;

        d.footer1 = "Score: this app's Finger-Fitts PD/non-PD models. Hit rate:";
        d.footer2 = "De Vleeschhauwer 2021; tap offset assumed. Not a diagnosis.";
        return d;
    }
}
