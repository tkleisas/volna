package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SensorModulationTest {
    @Test
    public void sourceCatalogIncludesSensorsAndTouch() {
        assertEquals(14, SensorModulation.SOURCE_COUNT);
        assertEquals("Accel X", SensorModulation.sourceName(SensorModulation.ACCEL_X));
        assertEquals("Mag magnitude",
                SensorModulation.sourceName(SensorModulation.MAG_MAGNITUDE));
        assertEquals("Touch X", SensorModulation.sourceName(SensorModulation.TOUCH_X));
        assertEquals("Touch Y", SensorModulation.sourceName(SensorModulation.TOUCH_Y));
        assertEquals("Touch ΔX",
                SensorModulation.sourceName(SensorModulation.TOUCH_DELTA_X));
        assertEquals("Touch ΔY",
                SensorModulation.sourceName(SensorModulation.TOUCH_DELTA_Y));
        assertEquals("None", SensorModulation.sourceName(-1));
        assertEquals("None", SensorModulation.sourceName(14));
    }

    @Test
    public void sensorAndTouchSourcesAreClassifiedSeparately() {
        for (int source = 0; source < 10; source++) {
            assertTrue(SensorModulation.isSensorSource(source));
            assertFalse(SensorModulation.isTouchSource(source));
        }
        for (int source = 10; source < 14; source++) {
            assertFalse(SensorModulation.isSensorSource(source));
            assertTrue(SensorModulation.isTouchSource(source));
        }

        assertTrue(SensorModulation.isAccelerometerSource(0));
        assertTrue(SensorModulation.isAccelerometerSource(2));
        assertFalse(SensorModulation.isAccelerometerSource(3));
        assertTrue(SensorModulation.isGyroSource(3));
        assertTrue(SensorModulation.isGyroSource(5));
        assertFalse(SensorModulation.isGyroSource(6));
        assertTrue(SensorModulation.isMagnetometerSource(6));
        assertTrue(SensorModulation.isMagnetometerSource(9));
        assertFalse(SensorModulation.isMagnetometerSource(10));
    }

    @Test
    public void relativeTouchSourcesAreDistinct() {
        assertTrue(SensorModulation.TOUCH_DELTA_X != SensorModulation.TOUCH_X);
        assertTrue(SensorModulation.TOUCH_DELTA_Y != SensorModulation.TOUCH_Y);
        assertEquals(SensorModulation.TOUCH_X + 2, SensorModulation.TOUCH_DELTA_X);
        assertEquals(SensorModulation.TOUCH_Y + 2, SensorModulation.TOUCH_DELTA_Y);
    }
}
