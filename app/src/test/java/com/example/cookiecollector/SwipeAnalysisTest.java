package com.example.cookiecollector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;

public class SwipeAnalysisTest {
    private static final float PX_PER_MM = 16f;

    /**
     * A swipe with a bell-shaped speed profile, 120 samples. Its duration is set from its actual
     * path length with MT = interceptMs + slope * L, as in the paper's linear model.
     */
    private static ArrayList<float[]> swipe(float lengthMm, float interceptMs, float slope, float wobbleMm, int bumps, Random rnd) {
        int steps = 120;
        float[][] xy = new float[steps + 1][2];
        float path = 0;
        for (int i = 0; i <= steps; i++) {
            double u = (double) i / steps;
            // Minimum-jerk position, plus extra bumps in progress for a fragmented movement
            double progress = 10 * Math.pow(u, 3) - 15 * Math.pow(u, 4) + 6 * Math.pow(u, 5);
            progress += bumps * 0.02 * Math.sin(2 * Math.PI * bumps * u);
            xy[i][0] = (float) (progress * lengthMm);
            xy[i][1] = (float) (wobbleMm * Math.sin(3 * Math.PI * u) + rnd.nextGaussian() * wobbleMm * 0.05);
            if (i > 0) path += (float) Math.hypot(xy[i][0] - xy[i - 1][0], xy[i][1] - xy[i - 1][1]);
        }
        float durationMs = interceptMs + slope * path;
        ArrayList<float[]> s = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            s.add(new float[]{durationMs * i / steps, 100 + xy[i][0] * PX_PER_MM, 500 + xy[i][1] * PX_PER_MM});
        }
        return s;
    }

    private static ArrayList<ArrayList<float[]>> group(boolean pd, Random rnd) {
        ArrayList<ArrayList<float[]>> strokes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            float length = 30 + rnd.nextFloat() * 40;
            // Durations follow each group's fit in the paper
            if (pd) strokes.add(swipe(length, 97.1f, 8.49f, 6f, 3, rnd));
            else strokes.add(swipe(length, 44.4f, 3.12f, 0.4f, 0, rnd));
        }
        return strokes;
    }

    @Test
    public void nonPdLikeSwipesScoreLow() {
        SwipeAnalysis.Report r = SwipeAnalysis.analyze(group(false, new Random(1)), PX_PER_MM);
        assertEquals(20, r.validStrokes);
        assertTrue("duration " + r.meanDurationS, r.meanDurationS < 0.3f);
        assertTrue("straightness " + r.meanStraightness, r.meanStraightness > 0.98f);
        assertEquals(3.12f, r.slopeMsPerMm, 0.3f);
        assertTrue("score " + r.pdScore, r.pdScore >= 0 && r.pdScore < 0.35f);
    }

    @Test
    public void pdLikeSwipesScoreHigh() {
        SwipeAnalysis.Report r = SwipeAnalysis.analyze(group(true, new Random(2)), PX_PER_MM);
        assertTrue("duration " + r.meanDurationS, r.meanDurationS > 0.4f);
        assertTrue("straightness " + r.meanStraightness, r.meanStraightness < 0.97f);
        assertEquals(8.49f, r.slopeMsPerMm, 1.5f);
        assertTrue("submovements " + r.meanSubmovements, r.meanSubmovements > 1.5f);
        assertTrue("score " + r.pdScore, r.pdScore > 0.65f);
    }

    @Test
    public void tapsAndTooFewSwipesGiveNoScore() {
        ArrayList<ArrayList<float[]>> strokes = new ArrayList<>();
        Random rnd = new Random(3);
        for (int i = 0; i < 10; i++) strokes.add(swipe(2, 80, 3, 0, 0, rnd));   // taps, too short
        for (int i = 0; i < 3; i++) strokes.add(swipe(50, 44.4f, 3.12f, 0.4f, 0, rnd));
        SwipeAnalysis.Report r = SwipeAnalysis.analyze(strokes, PX_PER_MM);
        assertEquals(3, r.validStrokes);
        assertEquals(-1f, r.pdScore, 0);
    }
}
