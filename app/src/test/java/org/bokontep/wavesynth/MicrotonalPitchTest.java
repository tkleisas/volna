package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MicrotonalPitchTest {
    private static final double EPS = 1.0e-6;

    @Test
    public void tet12ChromaticUsesStandardNotes() {
        MicrotonalPitchMapper mapper = mapper(60, chromatic(), 12, 440.0, 2.0, 100);

        MicrotonalPitchMapper.Pitch atZero = mapper.pitchAt(0, 0);
        assertEquals(60.0, atZero.targetSemitones, EPS);
        assertEquals(60, atZero.cellNote);
        assertEquals(0.0, atZero.bendSemitones, EPS);

        MicrotonalPitchMapper.Pitch halfway = mapper.pitchAt(50, 0);
        assertEquals(60.5, halfway.targetSemitones, EPS);
        assertEquals(60, halfway.cellNote);
        assertEquals(0.5, halfway.bendSemitones, EPS);

        MicrotonalPitchMapper.Pitch next = mapper.pitchAt(100, 0);
        assertEquals(61.0, next.targetSemitones, EPS);
        assertEquals(61, next.cellNote);
        assertEquals(0.0, next.bendSemitones, EPS);

        assertEquals(72.0, mapper.pitchAt(0, 12).targetSemitones, EPS);
    }

    @Test
    public void tet24UsesPitchBendInsteadOfRemappedNotes() {
        MicrotonalPitchMapper mapper = mapper(69, chromatic(), 24, 440.0, 2.0, 100);

        MicrotonalPitchMapper.Pitch a4 = mapper.pitchAt(0, 0);
        assertEquals(69.0, a4.targetSemitones, EPS);
        assertEquals(69, a4.cellNote);

        MicrotonalPitchMapper.Pitch quarterTone = mapper.pitchAt(100, 0);
        assertEquals(69.5, quarterTone.targetSemitones, EPS);
        assertEquals(70, quarterTone.cellNote);
        assertEquals(-0.5, quarterTone.bendSemitones, EPS);

        assertEquals(70.0, mapper.pitchAt(200, 0).targetSemitones, EPS);
    }

    @Test
    public void tet24RootNoteMatchesItsTwelveTetPitch() {
        MicrotonalPitchMapper mapper = mapper(60, chromatic(), 24, 440.0, 2.0, 100);
        assertEquals(60.0, mapper.pitchAt(0, 0).targetSemitones, 0.01);
    }

    @Test
    public void scaleYZoneOffsetsShiftByOctaves() {
        MicrotonalPitchMapper mapper = mapper(60, major(), 12, 440.0, 2.0, 100);
        assertEquals(72.0, mapper.pitchAt(0, 12).targetSemitones, EPS);
        assertEquals(84.0, mapper.pitchAt(0, 24).targetSemitones, EPS);
    }

    @Test
    public void majorScaleInterpolatesBetweenDegrees() {
        MicrotonalPitchMapper mapper = mapper(60, major(), 12, 440.0, 2.0, 100);

        assertEquals(60.0, mapper.pitchAt(0, 0).targetSemitones, EPS);
        assertEquals(62.0, mapper.pitchAt(100, 0).targetSemitones, EPS);
        assertEquals(64.0, mapper.pitchAt(200, 0).targetSemitones, EPS);
        assertEquals(65.0, mapper.pitchAt(300, 0).targetSemitones, EPS);
        assertEquals(61.0, mapper.pitchAt(50, 0).targetSemitones, EPS);
    }

    @Test
    public void tuneShiftsTheWholeKeyboard() {
        MicrotonalPitchMapper mapper = mapper(69, chromatic(), 12, 432.0, 2.0, 100);
        double expected = 69.0 + 12.0 * Math.log(432.0 / 440.0) / Math.log(2.0);
        assertEquals(expected, mapper.pitchAt(0, 0).targetSemitones, EPS);
    }

    @Test
    public void octaveFactorChangesPitchSpacing() {
        MicrotonalPitchMapper mapper = mapper(60, chromatic(), 12, 440.0, 1.5, 100);
        double expected = 69.0 + Math.log(1.5) / Math.log(2.0) * (60.0 - 69.0);
        assertEquals(expected, mapper.pitchAt(0, 0).targetSemitones, EPS);
    }

    @Test
    public void cellNoteIsAlwaysInMidiRange() {
        MicrotonalPitchMapper mapper = mapper(60, chromatic(), 24, 440.0, 2.0, 100);
        assertTrue(mapper.pitchAt(0, 0).cellNote >= 0);
        assertTrue(mapper.pitchAt(0, 0).cellNote <= 127);
        assertTrue(mapper.pitchAt(100000, 0).cellNote <= 127);
    }

    @Test
    public void noteChangesAtCellBoundaryNotHalfway() {
        MicrotonalPitchMapper mapper = mapper(60, chromatic(), 12, 440.0, 2.0, 100);

        assertEquals(60, mapper.pitchAt(0, 0).cellNote);
        assertEquals(60, mapper.pitchAt(50, 0).cellNote);
        assertEquals(60, mapper.pitchAt(99, 0).cellNote);
        assertEquals(61, mapper.pitchAt(100, 0).cellNote);
        assertEquals(61, mapper.pitchAt(150, 0).cellNote);
        assertEquals(61, mapper.pitchAt(199, 0).cellNote);
        assertEquals(62, mapper.pitchAt(200, 0).cellNote);
    }

    @Test
    public void nonLegatoReanchorsWhenNoteCrosses() {
        MicrotonalVoice voice = new MicrotonalVoice();
        MicrotonalVoice.Transition start = voice.start(pitch(60.0, 60, 0.0));
        assertEquals(60, start.noteOn);
        assertEquals(-1, start.noteOff);

        MicrotonalVoice.Transition update = voice.update(pitch(62.4, 62, 0.4), false);
        assertEquals(60, update.noteOff);
        assertEquals(62, update.noteOn);
        assertEquals(62, update.anchorNote);
        assertEquals(0.4, update.bendSemitones, EPS);

        MicrotonalVoice.Transition next = voice.update(pitch(62.6, 63, -0.4), false);
        assertEquals(62, next.noteOff);
        assertEquals(63, next.noteOn);
        assertEquals(-0.4, next.bendSemitones, EPS);
    }

    @Test
    public void legatoKeepsAnchorWithinBendRange() {
        MicrotonalVoice voice = new MicrotonalVoice();
        voice.start(pitch(60.0, 60, 0.0));

        MicrotonalVoice.Transition slide = voice.update(pitch(60.8, 61, -0.2), true);
        assertEquals(-1, slide.noteOff);
        assertEquals(-1, slide.noteOn);
        assertEquals(60, slide.anchorNote);
        assertEquals(0.8, slide.bendSemitones, EPS);

        MicrotonalVoice.Transition wide = voice.update(pitch(100.0, 100, 0.0), true);
        assertEquals(-1, wide.noteOff);
        assertEquals(-1, wide.noteOn);
        assertEquals(60, wide.anchorNote);
        assertEquals(40.0, wide.bendSemitones, EPS);
    }

    @Test
    public void legatoReanchorsBeyondBendRange() {
        MicrotonalVoice voice = new MicrotonalVoice();
        voice.start(pitch(60.0, 60, 0.0));

        MicrotonalVoice.Transition transition = voice.update(
                pitch(110.0, 110, 0.0), true);
        assertEquals(60, transition.noteOff);
        assertEquals(110, transition.noteOn);
        assertEquals(110, transition.anchorNote);
        assertEquals(0.0, transition.bendSemitones, EPS);
        assertTrue(transition.reanchored);
    }

    @Test
    public void stopReleasesCurrentAnchor() {
        MicrotonalVoice voice = new MicrotonalVoice();
        voice.start(pitch(60.0, 60, 0.0));
        voice.update(pitch(61.0, 61, 0.0), false);

        MicrotonalVoice.Transition stop = voice.stop();
        assertEquals(61, stop.noteOff);
        assertEquals(-1, stop.noteOn);
        assertFalse(voice.isActive());
    }

    @Test
    public void mpeChannelAllocatorUsesMemberChannelsAndReusesThem() {
        MpeChannelAllocator allocator = new MpeChannelAllocator();
        for (int i = 0; i < 15; i++) {
            int channel = allocator.allocate();
            assertTrue(channel >= 1 && channel <= 15);
            assertTrue(allocator.isUsed(channel));
        }
        allocator.release(7);
        assertFalse(allocator.isUsed(7));
        assertEquals(7, allocator.allocate());
    }

    private static MicrotonalPitchMapper mapper(int root, int[] scale, int tet,
                                                double tune, double octave,
                                                int xNoteScale) {
        return new MicrotonalPitchMapper(root, scale, tet, tune, octave,
                xNoteScale);
    }

    private static MicrotonalPitchMapper.Pitch pitch(double target, int cellNote,
                                                     double bend) {
        return new MicrotonalPitchMapper.Pitch(target, cellNote, bend, 0, 0.0);
    }

    private static int[] chromatic() {
        int[] row = new int[13];
        row[0] = 12;
        for (int i = 0; i < 12; i++) {
            row[1 + i] = i;
        }
        return row;
    }

    private static int[] major() {
        int[] row = new int[13];
        row[0] = 7;
        int[] degrees = {0, 2, 4, 5, 7, 9, 11};
        for (int i = 0; i < degrees.length; i++) {
            row[1 + i] = degrees[i];
        }
        return row;
    }
}
