package com.example.cookiecollector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class TapAnalysisTest {

    /** A 41-trial game where each tap takes the given group's predicted mean movement time. */
    private static UserAndStudyInfo game(boolean pd, float offsetMm, int misses) {
        int diameter = 16;
        UserAndStudyInfo info = new UserAndStudyInfo(4, 3, diameter, 41);
        Random rnd = new Random(pd ? 2 : 1);
        for (int i = 0; i < 41; i++) {
            // Targets on a 4x3 grid with 22 mm between centers
            float x = 22 * rnd.nextInt(3), y = 22 * rnd.nextInt(4);
            info.addToCorrectCoordinates(i, x, y);
            float[] prev = i == 0 ? new float[]{x, y} : info.getCorrectCoordinates(i - 1);
            float d = (float) Math.hypot(prev[0] - x, prev[1] - y);
            long mt = (long) ParkinsonsProbabilityCalculator.predictedMeanMt(pd, Math.max(1, d), diameter);
            boolean hit = i >= misses;
            float off = hit ? offsetMm : 10;
            info.addTouchpoint(i, x + off, y, mt, hit);
        }
        return info;
    }

    @Test
    public void nonPdTimingScoresBelowPdTiming() {
        ReportData nonPd = TapAnalysis.analyze(game(false, 1.5f, 1));
        ReportData pd = TapAnalysis.analyze(game(true, 3f, 4));
        assertTrue("non-PD " + nonPd.score, nonPd.score >= 0 && nonPd.score < 0.5f);
        assertTrue("PD " + pd.score, pd.score > 0.5f);
        assertEquals(5, nonPd.features.size());
        // 40 taps 1.5 mm from center and one 10 mm miss
        assertEquals((40 * 1.5f + 10) / 41, nonPd.features.get(4).you, 0.01f);
    }

    @Test
    public void hitRateCountsFirstTouches() {
        ReportData r = TapAnalysis.analyze(game(false, 1.5f, 4));
        // 37 of 41 hit
        assertEquals(100f * 37 / 41, r.features.get(3).you, 0.01f);
    }

    @Test
    public void tooFewTapsGiveNoScore() {
        UserAndStudyInfo info = new UserAndStudyInfo(4, 3, 16, 12);
        ReportData r = TapAnalysis.analyze(info);
        assertEquals(-1f, r.score, 0);
    }
}
