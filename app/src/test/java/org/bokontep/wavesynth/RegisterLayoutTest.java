package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RegisterLayoutTest {
    @Test
    public void defaultsMatchTheExistingThreeZoneOctaveLayout() {
        RegisterLayout layout = new RegisterLayout();
        assertEquals(3, layout.count());
        assertEquals(RegisterLayout.UNIT_SEMITONES, layout.unit());
        assertEquals(0, layout.getSemitoneOffset(0));
        assertEquals(12, layout.getSemitoneOffset(1));
        assertEquals(24, layout.getSemitoneOffset(2));
    }

    @Test
    public void countIsClampedToSupportedRange() {
        RegisterLayout layout = new RegisterLayout();
        layout.setCount(0);
        assertEquals(1, layout.count());
        layout.setCount(9);
        assertEquals(8, layout.count());
    }

    @Test
    public void octaveAndGuitarPresetsFillRegisters() {
        RegisterLayout layout = new RegisterLayout();
        layout.applyPreset(RegisterLayout.PRESET_OCTAVES, 7);
        assertEquals(36, layout.getSemitoneOffset(3));

        layout.applyPreset(RegisterLayout.PRESET_GUITAR, 7);
        assertEquals(6, layout.count());
        assertEquals(RegisterLayout.UNIT_SEMITONES, layout.unit());
        assertEquals(0, layout.getSemitoneOffset(0));
        assertEquals(5, layout.getSemitoneOffset(1));
        assertEquals(10, layout.getSemitoneOffset(2));
        assertEquals(24, layout.getSemitoneOffset(5));
    }

    @Test
    public void bottomRegisterHasTheLowestIndexAndTopTheHighest() {
        RegisterLayout layout = new RegisterLayout();
        layout.setCount(3);
        assertEquals(2, layout.registerAt(0.0f, 300.0f));
        assertEquals(1, layout.registerAt(150.0f, 300.0f));
        assertEquals(0, layout.registerAt(299.0f, 300.0f));

        layout.setCount(1);
        assertEquals(0, layout.registerAt(0.0f, 300.0f));
        assertEquals(0, layout.registerAt(299.0f, 300.0f));
    }

    @Test
    public void scaleStepPresetAndOffsetUseDegrees() {
        RegisterLayout layout = new RegisterLayout();
        layout.setUnit(RegisterLayout.UNIT_SCALE_STEPS, 7);
        layout.applyPreset(RegisterLayout.PRESET_POWER, 7);
        assertEquals(RegisterLayout.UNIT_SCALE_STEPS, layout.unit());
        assertEquals(0, layout.getDegreeOffset(0));
        assertEquals(4, layout.getDegreeOffset(1));
        assertEquals(7, layout.getDegreeOffset(2));
    }

    @Test
    public void jsonRoundTripPreservesCountUnitAndOffsets() {
        RegisterLayout layout = new RegisterLayout();
        layout.setCount(5);
        layout.setUnit(RegisterLayout.UNIT_SCALE_STEPS, 7);
        layout.setOffset(2, 3);

        RegisterLayout restored = RegisterLayout.fromJson(layout.toJson());
        assertEquals(5, restored.count());
        assertEquals(RegisterLayout.UNIT_SCALE_STEPS, restored.unit());
        assertEquals(3, restored.getDegreeOffset(2));
    }
}
