package com.example.cookiecollector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class SpiralAnalysisTest {

    /**
     * One spiral traced along a 140 mm tunnel at the given speed, with sideways wobble, uneven
     * speed and a number of wall bumps. Samples every 10 ms.
     */
    private static SpiralTunnelView.Result spiral(float speed, float wobbleMm, float speedJitter, int bumps, Random rnd) {
        SpiralTunnelView.Result r = new SpiralTunnelView.Result();
        r.tunnelLengthMm = 140;
        r.tunnelWidthMm = 11.6f;
        r.wallContactMm = 3f;
        r.samples = new ArrayList<>();
        float arc = 0;
        long t = 0;
        while (arc < r.tunnelLengthMm) {
            float v = Math.max(1, speed * (1 + speedJitter * (float) Math.sin(t / 150.0)));
            float dev = (float) (wobbleMm * Math.sin(t / 300.0) + rnd.nextGaussian() * wobbleMm * 0.2);
            r.samples.add(new float[]{t, arc, 0, arc, dev, Math.abs(dev) > 3 ? 1 : 0});
            arc += v * 0.01f;
            t += 10;
        }
        r.millis = t;
        r.bumps = bumps;
        r.centerX = new float[]{0, 10};
        r.centerY = new float[]{0, 10};
        return r;
    }

    @Test
    public void steadyAccurateTracingScoresLow() {
        SpiralAnalysis.Summary s = SpiralAnalysis.summarize(Collections.singletonList(spiral(36, 1.2f, 0.3f, 0, new Random(1))));
        assertEquals(36, s.speed, 2);
        assertTrue("score " + s.score, s.score < 0.35f);
    }

    @Test
    public void slowWobblyTracingScoresHigh() {
        ArrayList<SpiralTunnelView.Result> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) results.add(spiral(20, 2.4f, 0.9f, 3, new Random(i)));
        SpiralAnalysis.Summary s = SpiralAnalysis.summarize(results);
        assertEquals(3, s.bumpsPerSpiral, 0.01f);
        assertTrue("score " + s.score, s.score > 0.65f);
    }

    @Test
    public void constantSpeedHasNoUnevenness() {
        SpiralTunnelView.Result r = spiral(30, 0, 0, 0, new Random(3));
        ArrayList<Float> speeds = SpiralAnalysis.binnedSpeeds(r.samples);
        assertTrue(speeds.size() > 30);
        for (float v : speeds) assertEquals(30, v, 0.5f);
        assertEquals(0, SpiralAnalysis.summarize(Collections.singletonList(r)).speedCv, 0.02f);
    }

    @Test
    public void reportHasFiveFeaturesAndTwoCharts() {
        ReportData d = SpiralAnalysis.analyze(Collections.singletonList(spiral(30, 1, 0.3f, 1, new Random(4))));
        assertEquals(5, d.features.size());
        assertTrue(d.leftChart != null && d.rightChart != null);
    }
}
