package org.bokontep.wavesynth;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Configurable vertical register count, units, and offsets. */
public final class RegisterLayout {
    public static final int MIN_REGISTERS = 1;
    public static final int MAX_REGISTERS = 8;
    public static final int UNIT_SEMITONES = 0;
    public static final int UNIT_SCALE_STEPS = 1;
    public static final int PRESET_CUSTOM = 0;
    public static final int PRESET_OCTAVES = 1;
    public static final int PRESET_POWER = 2;
    public static final int PRESET_MAJOR = 3;
    public static final int PRESET_MINOR = 4;
    public static final int PRESET_GUITAR = 5;
    public static final String PREF_KEY = "registerLayoutV1";

    public static final int[] COLORS = {
            0xFF5CFFC0, 0xFFFFB35C, 0xFF7AB8FF, 0xFFC792FF,
            0xFFFF7AA8, 0xFF5CE1E6, 0xFFFFD166, 0xFF9BE564
    };

    private int count = 3;
    private int unit = UNIT_SEMITONES;
    private final int[] semitones = {0, 12, 24, 36, 48, 60, 72, 84};
    private final int[] degrees = {0, 7, 14, 21, 28, 35, 42, 49};
    private boolean customDegrees;
    private int preset = PRESET_OCTAVES;

    public int count() {
        return count;
    }

    public int unit() {
        return unit;
    }

    public int preset() {
        return preset;
    }

    public int getOffset(int register) {
        int index = clampRegister(register);
        return unit == UNIT_SEMITONES ? semitones[index] : degrees[index];
    }

    /** Maps a touch Y to a register index, with register 0 at the bottom. */
    public int registerAt(float y, float height) {
        if (height <= 0.0f) {
            return 0;
        }
        int fromTop = (int) (y / height * count);
        fromTop = Math.max(0, Math.min(count - 1, fromTop));
        return count - 1 - fromTop;
    }

    public int getSemitoneOffset(int register) {
        return semitones[clampRegister(register)];
    }

    public int getDegreeOffset(int register) {
        return degrees[clampRegister(register)];
    }

    public void setCount(int count) {
        this.count = Math.max(MIN_REGISTERS, Math.min(MAX_REGISTERS, count));
    }

    public void setUnit(int unit, int scaleSize) {
        this.unit = unit == UNIT_SCALE_STEPS ? UNIT_SCALE_STEPS
                : UNIT_SEMITONES;
        ensureDegreeDefaults(scaleSize);
    }

    public void setOffset(int register, int value) {
        int index = clampRegister(register);
        if (unit == UNIT_SEMITONES) {
            semitones[index] = Math.max(-96, Math.min(96, value));
        } else {
            degrees[index] = Math.max(-64, Math.min(64, value));
            customDegrees = true;
        }
        preset = PRESET_CUSTOM;
    }

    public void applyPreset(int preset, int scaleSize) {
        this.preset = preset;
        if (preset == PRESET_GUITAR) {
            unit = UNIT_SEMITONES;
            count = 6;
            int[] guitar = {0, 5, 10, 15, 19, 24, 29, 34};
            System.arraycopy(guitar, 0, semitones, 0, MAX_REGISTERS);
            ensureDegreeDefaults(scaleSize);
            return;
        }
        if (preset == PRESET_OCTAVES) {
            for (int i = 0; i < MAX_REGISTERS; i++) {
                semitones[i] = i * 12;
            }
            if (unit == UNIT_SCALE_STEPS) {
                for (int i = 0; i < MAX_REGISTERS; i++) {
                    degrees[i] = i * Math.max(1, scaleSize);
                }
                customDegrees = true;
            }
            return;
        }
        int[] semitonePattern;
        int[] degreePattern;
        switch (preset) {
            case PRESET_POWER:
                semitonePattern = new int[] {0, 7, 12, 19, 24, 31, 36, 43};
                degreePattern = new int[] {0, 4, scaleSize, scaleSize + 4,
                        2 * scaleSize, 2 * scaleSize + 4, 3 * scaleSize,
                        3 * scaleSize + 4};
                break;
            case PRESET_MAJOR:
                semitonePattern = new int[] {0, 4, 7, 12, 16, 19, 24, 28};
                degreePattern = new int[] {0, 2, 4, scaleSize, scaleSize + 2,
                        scaleSize + 4, 2 * scaleSize, 2 * scaleSize + 2};
                break;
            case PRESET_MINOR:
                semitonePattern = new int[] {0, 3, 7, 12, 15, 19, 24, 27};
                degreePattern = new int[] {0, 2, 4, scaleSize, scaleSize + 2,
                        scaleSize + 4, 2 * scaleSize, 2 * scaleSize + 2};
                break;
            default:
                return;
        }
        System.arraycopy(semitonePattern, 0, semitones, 0, MAX_REGISTERS);
        System.arraycopy(degreePattern, 0, degrees, 0, MAX_REGISTERS);
        customDegrees = true;
    }

    public void ensureDegreeDefaults(int scaleSize) {
        if (unit != UNIT_SCALE_STEPS || preset == PRESET_CUSTOM) {
            return;
        }
        int size = Math.max(1, scaleSize);
        switch (preset) {
            case PRESET_OCTAVES:
                for (int i = 0; i < MAX_REGISTERS; i++) {
                    degrees[i] = i * size;
                }
                break;
            case PRESET_POWER:
                setDegreePattern(new int[] {0, 4, size, size + 4,
                        2 * size, 2 * size + 4, 3 * size, 3 * size + 4});
                break;
            case PRESET_MAJOR:
            case PRESET_MINOR:
                setDegreePattern(new int[] {0, 2, 4, size, size + 2,
                        size + 4, 2 * size, 2 * size + 2});
                break;
            default:
                break;
        }
    }

    private void setDegreePattern(int[] pattern) {
        System.arraycopy(pattern, 0, degrees, 0, MAX_REGISTERS);
        customDegrees = true;
    }

    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("count", count);
            root.put("unit", unit);
            root.put("preset", preset);
            root.put("semitone", toArray(semitones));
            root.put("degrees", toArray(degrees));
            root.put("customDegrees", customDegrees);
            return root.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    public static RegisterLayout fromJson(String json) {
        RegisterLayout layout = new RegisterLayout();
        if (json == null || json.isEmpty()) {
            return layout;
        }
        try {
            JSONObject root = new JSONObject(json);
            layout.count = Math.max(MIN_REGISTERS, Math.min(MAX_REGISTERS,
                    root.optInt("count", 3)));
            layout.unit = root.optInt("unit", UNIT_SEMITONES)
                    == UNIT_SCALE_STEPS ? UNIT_SCALE_STEPS : UNIT_SEMITONES;
            layout.preset = root.optInt("preset", PRESET_OCTAVES);
            readArray(root.optJSONArray("semitone"), layout.semitones);
            readArray(root.optJSONArray("degrees"), layout.degrees);
            layout.customDegrees = root.optBoolean("customDegrees", false);
        } catch (JSONException ignored) {
        }
        return layout;
    }

    public static RegisterLayout fromPreferences(AppPreferences prefs,
                                                int scaleSize) {
        String json = prefs.readString(PREF_KEY, "");
        RegisterLayout layout = fromJson(json);
        if (json == null || json.isEmpty()) {
            layout.semitones[0] = prefs.readInt("lowOffset", 0);
            layout.semitones[1] = prefs.readInt("midOffset", 12);
            layout.semitones[2] = prefs.readInt("highOffset", 24);
            layout.count = 3;
        }
        layout.ensureDegreeDefaults(scaleSize);
        return layout;
    }

    public void save(AppPreferences prefs) {
        prefs.writeString(PREF_KEY, toJson());
    }

    public static int colorFor(int register) {
        return COLORS[Math.floorMod(register, COLORS.length)];
    }

    private static int clampRegister(int register) {
        return Math.max(0, Math.min(MAX_REGISTERS - 1, register));
    }

    private static JSONArray toArray(int[] values) throws JSONException {
        JSONArray array = new JSONArray();
        for (int value : values) {
            array.put(value);
        }
        return array;
    }

    private static void readArray(JSONArray array, int[] target) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < target.length && i < array.length(); i++) {
            target[i] = array.optInt(i, target[i]);
        }
    }
}
