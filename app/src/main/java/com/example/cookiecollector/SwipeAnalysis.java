package com.example.cookiecollector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Kinematic analysis of the swipe game's strokes, following Bi, Chen and Gu, "Modeling and
 * Characterizing Smartphone Flick Kinematics in Parkinson's Disease". A stroke runs from
 * touch-down to touch-up. Feature definitions match the paper:
 * <ul>
 *   <li>path length: sum of distances between consecutive touch points (mm)</li>
 *   <li>duration: touch-down to touch-up (s)</li>
 *   <li>average speed: path length / duration, averaged over strokes (mm/s)</li>
 *   <li>straightness: endpoint displacement / path length</li>
 *   <li>time per mm: slope b of MT = a + bL fitted over the player's strokes (ms/mm)</li>
 *   <li>speed profile: speed on normalized time 0..1 at 101 points; submovements are local
 *       maxima that rise at least 10% of the stroke's peak speed above the adjacent minima</li>
 * </ul>
 *
 * The paper's trained classifier isn't available on the phone, so the PD estimate compares the
 * player with the paper's published participant-level group statistics instead: for each feature,
 * the log-likelihood ratio between the PD and non-PD group distributions, averaged over features
 * (they are strongly correlated, so they are not added up), with equal prior odds.
 * It is a screening estimate, not a diagnosis.
 */
final class SwipeAnalysis {
    private SwipeAnalysis() {}

    static final int PROFILE_POINTS = 101;
    private static final int MIN_SAMPLES = 3;
    private static final float MAX_DURATION_S = 10f;
    // Shorter strokes are taps or touches, not swipes
    private static final float MIN_LENGTH_MM = 10f;
    private static final int MIN_STROKES_FOR_ESTIMATE = 5;
    private static final int MIN_STROKES_FOR_SLOPE = 8;
    private static final float MIN_LENGTH_RANGE_FOR_SLOPE_MM = 20f;
    private static final double MAX_ABS_LLR = 3;

    // Published group values: participant-level means with 95% CI (n = 67 non-PD, 35 PD)
    static final Reference DURATION_S = Reference.fromCi(0.193, 0.171, 0.216, 67, 0.586, 0.401, 0.794, 35);
    static final Reference SPEED_MM_S = Reference.fromCi(313.3, 284.4, 342.7, 67, 139.9, 109.7, 178.1, 35);
    static final Reference STRAIGHTNESS = Reference.fromCi(0.988, 0.984, 0.991, 67, 0.886, 0.825, 0.955, 35);
    // Mixed-model slopes with the spread of participant-specific slopes (random-slope SD)
    static final Reference MS_PER_MM = new Reference(3.12, 2.1, 8.49, 6.4);
    static final double NON_PD_INTERCEPT_MS = 44.4;
    static final double PD_INTERCEPT_MS = 97.1;
    // Shown for comparison only; the paper reports no spread for these
    static final double NON_PD_MID_SHARE = 0.371, PD_MID_SHARE = 0.435;
    static final double NON_PD_PEAK_SPEED = 490, PD_PEAK_SPEED = 200;

    /** Mean and between-participant SD of a feature in each group. */
    static class Reference implements Serializable {
        final double nonPdMean, nonPdSd, pdMean, pdSd;

        Reference(double nonPdMean, double nonPdSd, double pdMean, double pdSd) {
            this.nonPdMean = nonPdMean;
            this.nonPdSd = nonPdSd;
            this.pdMean = pdMean;
            this.pdSd = pdSd;
        }

        /** Recovers the between-participant SD from a 95% CI of the mean: SD = SE * sqrt(n). */
        static Reference fromCi(double m0, double lo0, double hi0, int n0, double m1, double lo1, double hi1, int n1) {
            return new Reference(m0, (hi0 - lo0) / 3.92 * Math.sqrt(n0), m1, (hi1 - lo1) / 3.92 * Math.sqrt(n1));
        }
    }

    static class Report implements Serializable {
        int totalStrokes;
        int validStrokes;
        float[] lengthsMm = new float[0];
        float[] durationsS = new float[0];
        float[] meanProfile = new float[PROFILE_POINTS]; // mm/s on normalized time
        float meanLengthMm, meanDurationS, meanSpeed, meanStraightness, meanMidShare, meanSubmovements;
        float speedCv, straightnessCv;
        float slopeMsPerMm = Float.NaN, interceptMs = Float.NaN;
        boolean slopeUsed;
        // Probability-like score in 0..1, or -1 when there are too few swipes
        float pdScore = -1;
    }

    static Report analyze(List<ArrayList<float[]>> strokes, float pxPerMm) {
        Report r = new Report();
        r.totalStrokes = strokes.size();
        ArrayList<float[]> features = new ArrayList<>(); // {length, duration, speed, straightness, midShare, submovements}
        ArrayList<float[]> profiles = new ArrayList<>();
        for (ArrayList<float[]> s : strokes) {
            float[] profile = new float[PROFILE_POINTS];
            float[] f = strokeFeatures(s, pxPerMm, profile);
            if (f == null) continue;
            features.add(f);
            profiles.add(profile);
        }
        int n = features.size();
        r.validStrokes = n;
        if (n == 0) return r;

        r.lengthsMm = new float[n];
        r.durationsS = new float[n];
        double sumL = 0, sumT = 0, sumV = 0, sumV2 = 0, sumS = 0, sumS2 = 0, sumMid = 0, sumSub = 0;
        for (int i = 0; i < n; i++) {
            float[] f = features.get(i);
            r.lengthsMm[i] = f[0];
            r.durationsS[i] = f[1];
            sumL += f[0];
            sumT += f[1];
            sumV += f[2];
            sumV2 += f[2] * f[2];
            sumS += f[3];
            sumS2 += f[3] * f[3];
            sumMid += f[4];
            sumSub += f[5];
            for (int j = 0; j < PROFILE_POINTS; j++) r.meanProfile[j] += profiles.get(i)[j] / n;
        }
        r.meanLengthMm = (float) (sumL / n);
        r.meanDurationS = (float) (sumT / n);
        r.meanSpeed = (float) (sumV / n);
        r.meanStraightness = (float) (sumS / n);
        r.meanMidShare = (float) (sumMid / n);
        r.meanSubmovements = (float) (sumSub / n);
        r.speedCv = cv(sumV, sumV2, n);
        r.straightnessCv = cv(sumS, sumS2, n);

        // Least-squares MT = a + bL, with MT in ms
        float minL = Float.MAX_VALUE, maxL = 0;
        for (float l : r.lengthsMm) { minL = Math.min(minL, l); maxL = Math.max(maxL, l); }
        if (n >= 2 && maxL - minL > 1) {
            double mL = sumL / n, mT = sumT / n * 1000, sxy = 0, sxx = 0;
            for (int i = 0; i < n; i++) {
                double dl = r.lengthsMm[i] - mL;
                sxy += dl * (r.durationsS[i] * 1000 - mT);
                sxx += dl * dl;
            }
            r.slopeMsPerMm = (float) (sxy / sxx);
            r.interceptMs = (float) (mT - r.slopeMsPerMm * mL);
        }
        r.slopeUsed = n >= MIN_STROKES_FOR_SLOPE && maxL - minL >= MIN_LENGTH_RANGE_FOR_SLOPE_MM && !Float.isNaN(r.slopeMsPerMm);

        if (n >= MIN_STROKES_FOR_ESTIMATE) {
            double llr = logNormalLlr(DURATION_S.nonPdMean, DURATION_S.nonPdSd, DURATION_S.pdMean, DURATION_S.pdSd, r.meanDurationS)
                    + logNormalLlr(SPEED_MM_S.nonPdMean, SPEED_MM_S.nonPdSd, SPEED_MM_S.pdMean, SPEED_MM_S.pdSd, r.meanSpeed)
                    // Straightness is capped at 1, so its distance from a straight line is compared instead
                    + logNormalLlr(1 - STRAIGHTNESS.nonPdMean, STRAIGHTNESS.nonPdSd, 1 - STRAIGHTNESS.pdMean, STRAIGHTNESS.pdSd,
                            Math.max(1e-4, 1 - r.meanStraightness));
            int count = 3;
            if (r.slopeUsed) {
                llr += normalLlr(MS_PER_MM.nonPdMean, MS_PER_MM.nonPdSd, MS_PER_MM.pdMean, MS_PER_MM.pdSd, r.slopeMsPerMm);
                count++;
            }
            r.pdScore = (float) (1 / (1 + Math.exp(-llr / count)));
        }
        return r;
    }

    /**
     * Features of one stroke, or null if it is not a usable swipe. Also fills {@code profile}
     * with its speed (mm/s) at 101 points of normalized time.
     */
    private static float[] strokeFeatures(ArrayList<float[]> s, float pxPerMm, float[] profile) {
        if (s.size() < MIN_SAMPLES) return null;
        // Cumulative path length over time, merging samples with the same timestamp
        ArrayList<float[]> path = new ArrayList<>(); // {time s, distance mm}
        float length = 0;
        for (int i = 0; i < s.size(); i++) {
            if (i > 0) length += (float) Math.hypot(s.get(i)[1] - s.get(i - 1)[1], s.get(i)[2] - s.get(i - 1)[2]) / pxPerMm;
            float t = s.get(i)[0] / 1000f;
            if (!path.isEmpty() && t <= path.get(path.size() - 1)[0]) path.get(path.size() - 1)[1] = length;
            else path.add(new float[]{t, length});
        }
        float duration = s.get(s.size() - 1)[0] / 1000f;
        if (path.size() < MIN_SAMPLES || duration <= 0 || duration > MAX_DURATION_S || length < MIN_LENGTH_MM) return null;

        float[] first = s.get(0);
        float[] last = s.get(s.size() - 1);
        float displacement = (float) Math.hypot(last[1] - first[1], last[2] - first[2]) / pxPerMm;

        // Resample distance on normalized time, then differentiate for speed
        float[] dist = new float[PROFILE_POINTS];
        for (int j = 0, k = 0; j < PROFILE_POINTS; j++) {
            float t = duration * j / (PROFILE_POINTS - 1);
            while (k < path.size() - 2 && path.get(k + 1)[0] < t) k++;
            float[] a = path.get(k);
            float[] b = path.get(k + 1);
            float u = b[0] == a[0] ? 1 : Math.max(0, Math.min(1, (t - a[0]) / (b[0] - a[0])));
            dist[j] = a[1] + u * (b[1] - a[1]);
        }
        float step = duration / (PROFILE_POINTS - 1);
        for (int j = 0; j < PROFILE_POINTS; j++) {
            int a = Math.max(0, j - 1);
            int b = Math.min(PROFILE_POINTS - 1, j + 1);
            profile[j] = (dist[b] - dist[a]) / ((b - a) * step);
        }

        float midShare = dist[(PROFILE_POINTS - 1) / 2] / length;
        return new float[]{length, duration, length / duration, Math.min(1, displacement / length), midShare, submovements(profile)};
    }

    /** Local maxima that rise at least 10% of the peak speed above the minima on both sides. */
    private static int submovements(float[] v) {
        float peak = 0;
        for (float x : v) peak = Math.max(peak, x);
        ArrayList<Integer> maxima = new ArrayList<>();
        for (int i = 1; i < v.length - 1; i++) if (v[i] > v[i - 1] && v[i] >= v[i + 1]) maxima.add(i);
        int count = 0;
        for (int m = 0; m < maxima.size(); m++) {
            int i = maxima.get(m);
            int left = m == 0 ? 0 : maxima.get(m - 1);
            int right = m == maxima.size() - 1 ? v.length - 1 : maxima.get(m + 1);
            float leftMin = v[i], rightMin = v[i];
            for (int j = left; j <= i; j++) leftMin = Math.min(leftMin, v[j]);
            for (int j = i; j <= right; j++) rightMin = Math.min(rightMin, v[j]);
            if (v[i] - leftMin >= 0.1f * peak && v[i] - rightMin >= 0.1f * peak) count++;
        }
        return Math.max(1, count);
    }

    private static float cv(double sum, double sumSq, int n) {
        if (n < 2) return 0;
        double mean = sum / n;
        double var = Math.max(0, (sumSq - n * mean * mean) / (n - 1));
        return mean == 0 ? 0 : (float) (Math.sqrt(var) / mean);
    }

    /** log p_PD(x) - log p_nonPD(x) for log-normal groups matched to each mean and SD, clipped. */
    private static double logNormalLlr(double m0, double sd0, double m1, double sd1, double x) {
        return clip(logNormalLogPdf(m1, sd1, x) - logNormalLogPdf(m0, sd0, x));
    }

    private static double logNormalLogPdf(double mean, double sd, double x) {
        double s2 = Math.log(1 + sd * sd / (mean * mean));
        double mu = Math.log(mean) - s2 / 2;
        double z = Math.log(x) - mu;
        // The -log(x) term is the same for both groups and cancels in the ratio
        return -0.5 * Math.log(s2) - z * z / (2 * s2);
    }

    private static double normalLlr(double m0, double sd0, double m1, double sd1, double x) {
        double z0 = (x - m0) / sd0;
        double z1 = (x - m1) / sd1;
        return clip(Math.log(sd0 / sd1) - z1 * z1 / 2 + z0 * z0 / 2);
    }

    // One extreme feature shouldn't decide the result on its own
    private static double clip(double llr) { return Math.max(-MAX_ABS_LLR, Math.min(MAX_ABS_LLR, llr)); }
}
