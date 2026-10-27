package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TouchModulationTest {
    private static final float EPS = 0.0001f;

    @Test
    public void horizontalPositionIsBipolarAcrossSurface() {
        assertEquals(-1.0f, TouchModulation.normalizedX(0.0f, 200.0f), EPS);
        assertEquals(0.0f, TouchModulation.normalizedX(100.0f, 200.0f), EPS);
        assertEquals(1.0f, TouchModulation.normalizedX(200.0f, 200.0f), EPS);
        assertEquals(0.0f, TouchModulation.normalizedX(100.0f, 0.0f), EPS);
    }

    @Test
    public void verticalPositionIsPositiveAtTop() {
        assertEquals(1.0f, TouchModulation.normalizedY(0.0f, 200.0f), EPS);
        assertEquals(0.0f, TouchModulation.normalizedY(100.0f, 200.0f), EPS);
        assertEquals(-1.0f, TouchModulation.normalizedY(200.0f, 200.0f), EPS);
        assertEquals(0.0f, TouchModulation.normalizedY(100.0f, 0.0f), EPS);
    }

    @Test
    public void relativePositionStartsAtZeroAndFollowsMovement() {
        assertEquals(0.0f, TouchModulation.relativeX(100.0f, 100.0f, 200.0f), EPS);
        assertEquals(1.0f, TouchModulation.relativeX(200.0f, 100.0f, 200.0f), EPS);
        assertEquals(-1.0f, TouchModulation.relativeX(0.0f, 100.0f, 200.0f), EPS);
        assertEquals(1.0f, TouchModulation.relativeX(300.0f, 100.0f, 200.0f), EPS);

        assertEquals(0.0f, TouchModulation.relativeY(100.0f, 100.0f, 200.0f), EPS);
        assertEquals(1.0f, TouchModulation.relativeY(0.0f, 100.0f, 200.0f), EPS);
        assertEquals(-1.0f, TouchModulation.relativeY(200.0f, 100.0f, 200.0f), EPS);
        assertEquals(0.0f, TouchModulation.relativeX(100.0f, 100.0f, 0.0f), EPS);
    }

    @Test
    public void pitchBendIsZeroByDefaultAndClamped() {
        assertEquals(0.0, TouchModulation.scaledBend(0.5, 0.0f), 0.0001);
        assertEquals(0.25, TouchModulation.scaledBend(0.5, 0.5f), 0.0001);
        assertEquals(0.5, TouchModulation.scaledBend(0.5, 1.5f), 0.0001);
        assertEquals(0.0, TouchModulation.scaledBend(0.5, -1.0f), 0.0001);
    }
}
