package org.bokontep.wavesynth;

/**
 * Converts touch positions into standard MIDI notes plus a per-note pitch
 * offset. The note number is always a normal 0-127 MIDI note; tuning and
 * glissando live in the bend value instead of being baked into the note.
 */
public final class MicrotonalPitchMapper {
    public static final double BEND_RANGE_SEMITONES = 48.0;
    private static final double LOG2 = Math.log(2.0);

    private final int rootNote;
    private final int[] scale;
    private final int tet;
    private final double tune;
    private final double octaveFactor;
    private final int xNoteScale;

    public MicrotonalPitchMapper(int rootNote, int[] scaleRow, int tet,
                                 double tune, double octaveFactor,
                                 int xNoteScale) {
        if (scaleRow == null || scaleRow.length < 13 || scaleRow[0] <= 0) {
            throw new IllegalArgumentException("Invalid scale row");
        }
        if (tet <= 0 || xNoteScale <= 0 || tune <= 0.0 || octaveFactor <= 0.0) {
            throw new IllegalArgumentException("Invalid tuning parameters");
        }
        this.rootNote = rootNote;
        this.scale = scaleRow.clone();
        this.tet = tet;
        this.tune = tune;
        this.octaveFactor = octaveFactor;
        this.xNoteScale = xNoteScale;
    }

    public static final class Pitch {
        public final double targetSemitones;
        public final int nearestNote;
        public final double bendSemitones;
        public final int cell;
        public final double cellFraction;

        public Pitch(double targetSemitones, int nearestNote,
                     double bendSemitones, int cell, double cellFraction) {
            this.targetSemitones = targetSemitones;
            this.nearestNote = nearestNote;
            this.bendSemitones = bendSemitones;
            this.cell = cell;
            this.cellFraction = cellFraction;
        }
    }

    public Pitch pitchAt(float x, int offsetSemitones) {
        final double cellPosition = (double) x / xNoteScale;
        final int cell = (int) Math.floor(cellPosition);
        final double fraction = cellPosition - cell;
        final double first = pitchForCell(cell, offsetSemitones);
        final double next = pitchForCell(cell + 1, offsetSemitones);
        final double target = first + (next - first) * fraction;
        final int nearest = clampNote((int) Math.round(target));
        return new Pitch(target, nearest, target - nearest, cell, fraction);
    }

    public double pitchForCell(int cell, int offsetSemitones) {
        if (tet == 12) {
            final int notesInScale = scale[0];
            final int scaleSteps = (int) Math.round(
                    offsetSemitones * notesInScale / 12.0);
            final int relative = cell + scaleSteps;
            final int octave = Math.floorDiv(relative, notesInScale);
            final int degree = Math.floorMod(relative, notesInScale);
            final int note = rootNote + octave * 12 + scale[1 + degree];
            return 69.0 + Math.log(octaveFactor) / LOG2 * (note - 69.0) +
                    tuneOffset();
        }

        // TET != 12: rootNote is a normal MIDI note, so convert it to the
        // equivalent step in the current equal division of the octave.
        final double a4Step = 5.75 * tet;
        final double rootStep = a4Step + (rootNote - 69.0) * tet / 12.0;
        final double step = rootStep + cell + offsetSemitones * tet / 12.0;
        final double frequency = Math.pow(octaveFactor,
                (step - a4Step) / tet) * tune;
        return 69.0 + 12.0 * Math.log(frequency / 440.0) / LOG2;
    }

    public double tuneOffset() {
        return 12.0 * Math.log(tune / 440.0) / LOG2;
    }

    public static int clampNote(int note) {
        return Math.max(0, Math.min(127, note));
    }
}
