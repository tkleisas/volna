package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TouchExpressionTest {
    @Test
    public void velocityCurveSpansFullMidiRange() {
        assertEquals(1, TouchExpression.velocity(0.0f));
        assertEquals(127, TouchExpression.velocity(1.0f));
        assertTrue(TouchExpression.velocity(0.5f) > 70);
        assertTrue(TouchExpression.velocity(0.5f) < 90);
        assertTrue(TouchExpression.velocity(0.2f) > 30);
        assertTrue(TouchExpression.velocity(0.2f) < 60);
    }

    @Test
    public void unsupportedExpressionUsesDefaultVelocity() {
        assertEquals(TouchExpression.UNSUPPORTED_VELOCITY,
                TouchExpression.velocity(-1.0f));
        assertEquals(-1, TouchExpression.midiPressure(-1.0f));
        assertFalse(TouchExpression.isSupported(-1.0f));
    }

    @Test
    public void midiPressureIsNormalized() {
        assertEquals(0, TouchExpression.midiPressure(0.0f));
        assertEquals(64, TouchExpression.midiPressure(0.5f));
        assertEquals(127, TouchExpression.midiPressure(1.0f));
    }

    @Test
    public void smoothingFollowsInputWithoutOvershoot() {
        assertEquals(0.5f, TouchExpression.smooth(0.5f, -1.0f), 0.0f);
        assertEquals(0.65f, TouchExpression.smooth(0.5f, 1.0f), 0.0001f);
        assertEquals(0.35f, TouchExpression.smooth(0.5f, 0.0f), 0.0001f);
        assertTrue(TouchExpression.isSupported(0.0f));
    }
}
