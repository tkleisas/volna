package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ScaleDefinitionTest {
    @Test
    public void builtInsContainTet12AndTet24Scales() {
        List<ScaleDefinition> scales = ScaleDefinition.builtIns();
        assertTrue(scales.stream().anyMatch(s -> s.id.equals("12:major")));
        assertTrue(scales.stream().anyMatch(s -> s.id.equals("24:rast")));
        assertTrue(scales.stream().anyMatch(s -> s.id.equals("24:bayati")));

        for (ScaleDefinition scale : scales) {
            assertEquals(0, scale.degrees[0]);
            assertTrue(scale.degrees.length > 0);
            assertTrue(scale.degrees[scale.degrees.length - 1] < scale.tet);
            assertTrue(scale.builtIn);
        }
    }

    @Test
    public void chromaticCoversEveryEdoStep() {
        ScaleDefinition scale = ScaleDefinition.chromatic(24);
        assertEquals(24, scale.size());
        assertEquals(23, scale.degrees[23]);
        assertTrue(scale.isChromatic());
    }

    @Test
    public void invalidDegreesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScaleDefinition("bad", "bad", 12,
                        new int[] {1, 2, 4}, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaleDefinition("bad", "bad", 12,
                        new int[] {0, 2, 2}, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaleDefinition("bad", "bad", 12,
                        new int[] {0, 12}, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaleDefinition("", "bad", 12,
                        new int[] {0}, false));
    }

    @Test
    public void customScaleIsNotBuiltIn() {
        ScaleDefinition scale = new ScaleDefinition("user:x", "Mine", 12,
                new int[] {0, 2, 5, 7, 9}, false);
        assertFalse(scale.builtIn);
        assertEquals(5, scale.size());
    }
}
