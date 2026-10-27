package org.bokontep.wavesynth;

/**
 * Per-touch note state for the hybrid gesture model:
 * - legato keeps one anchor note and slides with a wide bend;
 * - non-legato follows the cell note and re-triggers at cell boundaries;
 * - both re-anchor when the bend range is exceeded.
 */
public final class MicrotonalVoice {
    public static final double BEND_RANGE = MicrotonalPitchMapper.BEND_RANGE_SEMITONES;

    public static final class Transition {
        public final int noteOff;
        public final int noteOn;
        public final int anchorNote;
        public final double bendSemitones;
        public final boolean reanchored;

        Transition(int noteOff, int noteOn, int anchorNote, double bendSemitones,
                   boolean reanchored) {
            this.noteOff = noteOff;
            this.noteOn = noteOn;
            this.anchorNote = anchorNote;
            this.bendSemitones = bendSemitones;
            this.reanchored = reanchored;
        }
    }

    private boolean active;
    private int anchorNote = -1;
    private double bendSemitones;

    public boolean isActive() {
        return active;
    }

    public int anchorNote() {
        return anchorNote;
    }

    public double bendSemitones() {
        return bendSemitones;
    }

    public double currentPitch() {
        return anchorNote + bendSemitones;
    }

    public Transition start(MicrotonalPitchMapper.Pitch pitch) {
        active = true;
        anchorNote = pitch.cellNote;
        bendSemitones = clampBend(pitch.targetSemitones - anchorNote);
        return new Transition(-1, anchorNote, anchorNote, bendSemitones, false);
    }

    public Transition update(MicrotonalPitchMapper.Pitch pitch, boolean legato) {
        if (!active) {
            return start(pitch);
        }

        final int oldAnchor = anchorNote;
        final int cellNote = pitch.cellNote;
        boolean noteOff = false;
        boolean noteOn = false;

        if (legato) {
            if (Math.abs(pitch.targetSemitones - anchorNote) > BEND_RANGE) {
                anchorNote = cellNote;
                noteOff = true;
                noteOn = true;
            }
        } else if (cellNote != anchorNote) {
            anchorNote = cellNote;
            noteOff = true;
            noteOn = true;
        }

        bendSemitones = clampBend(pitch.targetSemitones - anchorNote);
        return new Transition(noteOff ? oldAnchor : -1,
                noteOn ? anchorNote : -1, anchorNote, bendSemitones,
                noteOff && noteOn);
    }

    public Transition stop() {
        final int oldAnchor = anchorNote;
        active = false;
        anchorNote = -1;
        bendSemitones = 0.0;
        return new Transition(oldAnchor, -1, -1, 0.0, false);
    }

    private static double clampBend(double bend) {
        return Math.max(-BEND_RANGE, Math.min(BEND_RANGE, bend));
    }
}
