package org.bokontep.wavesynth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A scale expressed as sorted EDO steps from the root. */
public final class ScaleDefinition {
    public final String id;
    public final String name;
    public final int tet;
    public final int[] degrees;
    public final boolean builtIn;

    public ScaleDefinition(String id, String name, int tet, int[] degrees,
                           boolean builtIn) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Scale needs an id");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Scale needs a name");
        }
        if (tet < 2) {
            throw new IllegalArgumentException("TET must be at least 2");
        }
        if (degrees == null || degrees.length == 0) {
            throw new IllegalArgumentException("Scale needs at least one degree");
        }
        int[] copy = degrees.clone();
        Arrays.sort(copy);
        if (copy[0] != 0) {
            throw new IllegalArgumentException("Scale must include the root");
        }
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] < 0 || copy[i] >= tet) {
                throw new IllegalArgumentException("Scale degree outside TET");
            }
            if (i > 0 && copy[i] == copy[i - 1]) {
                throw new IllegalArgumentException("Duplicate scale degree");
            }
        }
        this.id = id;
        this.name = name.trim();
        this.tet = tet;
        this.degrees = copy;
        this.builtIn = builtIn;
    }

    public int size() {
        return degrees.length;
    }

    public int degreeAt(int index) {
        return degrees[Math.floorMod(index, degrees.length)];
    }

    public boolean isChromatic() {
        return degrees.length == tet;
    }

    public static ScaleDefinition chromatic(int tet) {
        int[] degrees = new int[tet];
        for (int i = 0; i < tet; i++) {
            degrees[i] = i;
        }
        return new ScaleDefinition(tet + ":chromatic", "chromatic", tet,
                degrees, true);
    }

    public static List<ScaleDefinition> builtIns() {
        List<ScaleDefinition> scales = new ArrayList<>();
        scales.add(chromatic(12));
        scales.add(scale12("major", "major", 0, 2, 4, 5, 7, 9, 11));
        scales.add(scale12("natural-minor", "natural minor", 0, 2, 3, 5, 7, 8, 10));
        scales.add(scale12("harmonic-minor", "harmonic minor", 0, 2, 3, 5, 7, 8, 11));
        scales.add(scale12("melodic-minor", "ascending melodic minor", 0, 2, 3, 5, 7, 9, 11));
        scales.add(scale12("acoustic", "acoustic", 0, 2, 4, 6, 7, 9, 10));
        scales.add(scale12("major2", "major2", 0, 1, 2, 4, 5, 7, 8, 9, 10, 11));
        scales.add(scale12("minor2", "minor2", 0, 1, 4, 5, 7, 8, 10, 11));
        scales.add(scale12("pentatonic", "pentatonic", 0, 3, 5, 7, 10));
        scales.add(scale12("blues", "blues", 0, 3, 5, 6, 7, 10));
        scales.add(scale12("chinese-pentatonic", "chinese pentatonic", 0, 2, 5, 7, 9));
        scales.add(scale12("whole-tone", "whole tone", 0, 2, 4, 6, 8, 10));
        scales.add(scale12("whole-half", "whole half", 0, 2, 3, 5, 6, 8, 9, 11));
        scales.add(scale12("half-whole", "half whole", 0, 1, 3, 4, 6, 7, 9, 10));

        scales.add(scale24("chromatic", "quarter-tone chromatic",
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23));
        scales.add(scale24("major", "24-TET major", 0, 4, 8, 10, 14, 18, 22));
        scales.add(scale24("natural-minor", "24-TET natural minor",
                0, 4, 6, 10, 14, 16, 20));
        scales.add(scale24("neutral-pentatonic", "24-TET neutral pentatonic",
                0, 4, 7, 14, 18));
        scales.add(scale24("rast", "24-TET rast (approx.)",
                0, 4, 7, 10, 14, 18, 21));
        scales.add(scale24("bayati", "24-TET bayati (approx.)",
                0, 3, 6, 10, 14, 17, 20));
        return Collections.unmodifiableList(scales);
    }

    private static ScaleDefinition scale12(String slug, String name, int... degrees) {
        return new ScaleDefinition("12:" + slug, name, 12, degrees, true);
    }

    private static ScaleDefinition scale24(String slug, String name, int... degrees) {
        return new ScaleDefinition("24:" + slug, name, 24, degrees, true);
    }
}
