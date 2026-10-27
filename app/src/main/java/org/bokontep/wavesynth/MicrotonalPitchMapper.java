package org.bokontep.wavesynth;

/**
 * Converts touch positions into standard MIDI notes plus a per-note pitch
 * offset. The note number is always a normal 0-127 MIDI note; tuning and
 * glissando live in the bend value instead of being baked into the note. The
 * note anchor is the pitch of the cell containing the touch centre, so it
 * changes only when the centre crosses a vertical grid line.
 */
public final class MicrotonalPitchMapper {
    public static final double BEND_RANGE_SEMITONES = 48.0;
    private static final double LOG2 = Math.log(2.0);

    private final int rootNote;
    private final ScaleDefinition scale;
    private final int tet;
    private final double tune;
    private final double octaveFactor;
    private final int xNoteScale;

    public MicrotonalPitchMapper(int rootNote, ScaleDefinition scale,
                                 double tune, double octaveFactor,
                                 int xNoteScale) {
        if (scale == null) {
            throw new IllegalArgumentException("Invalid scale");
        }
        if (xNoteScale <= 0 || tune <= 0.0 || octaveFactor <= 0.0) {
            throw new IllegalArgumentException("Invalid tuning parameters");
        }
        this.rootNote = rootNote;
        this.scale = scale;
        this.tet = scale.tet;
        this.tune = tune;
        this.octaveFactor = octaveFactor;
        this.xNoteScale = xNoteScale;
    }

    public static final class Pitch {
        public final double targetSemitones;
        public final int cellNote;
        public final double bendSemitones;
        public final int cell;
        public final double cellFraction;

        public Pitch(double targetSemitones, int cellNote,
                     double bendSemitones, int cell, double cellFraction) {
            this.targetSemitones = targetSemitones;
            this.cellNote = cellNote;
            this.bendSemitones = bendSemitones;
            this.cell = cell;
            this.cellFraction = cellFraction;
        }
    }

    public Pitch pitchAt(float x, int offsetSemitones) {
        return pitchAt(x, offsetSemitones, 0);
    }

    public Pitch pitchAt(float x, int offsetSemitones, int cellOffset) {
        final double cellPosition = (double) x / xNoteScale;
        final int cell = (int) Math.floor(cellPosition);
        final double fraction = cellPosition - cell;
        final int effectiveCell = cell + cellOffset;
        final double first = pitchForCell(effectiveCell, offsetSemitones);
        final double next = pitchForCell(effectiveCell + 1, offsetSemitones);
        final double target = first + (next - first) * fraction;
        // The note anchor follows the visible cell, so it changes only when
        // the touch centre crosses a vertical grid line.
        final int cellNote = clampNote((int) Math.round(first));
        return new Pitch(target, cellNote, target - cellNote, effectiveCell,
                fraction);
    }

    public double pitchForCell(int cell, int offsetSemitones) {
        final int notesInScale = scale.size();
        final int octave = Math.floorDiv(cell, notesInScale);
        final int degree = Math.floorMod(cell, notesInScale);
        final double a4Step = 5.75 * tet;
        final double rootStep = a4Step + (rootNote - 69.0) * tet / 12.0;
        final double step = rootStep + scale.degrees[degree]
                + octave * tet + offsetSemitones * tet / 12.0;
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
