package org.bokontep.wavesynth;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Built-in and user-defined scales, filtered by TET. */
public final class ScaleStore {
    public static final String PREF_KEY = "customScalesV1";
    private final AppPreferences prefs;
    private final List<ScaleDefinition> custom = new ArrayList<>();

    public ScaleStore(AppPreferences prefs) {
        this.prefs = prefs;
        load();
    }

    public List<ScaleDefinition> scalesForTet(int tet) {
        List<ScaleDefinition> result = new ArrayList<>();
        for (ScaleDefinition scale : ScaleDefinition.builtIns()) {
            if (scale.tet == tet) {
                result.add(scale);
            }
        }
        if (tet == 12) {
            for (ScaleDefinition scale : custom) {
                if (scale.tet == 12) {
                    result.add(scale);
                }
            }
        }
        if (result.isEmpty()) {
            result.add(ScaleDefinition.chromatic(tet));
        }
        return result;
    }

    public ScaleDefinition find(String id, int tet) {
        if (id != null && !id.isEmpty()) {
            for (ScaleDefinition scale : ScaleDefinition.builtIns()) {
                if (scale.id.equals(id) && scale.tet == tet) {
                    return scale;
                }
            }
            for (ScaleDefinition scale : custom) {
                if (scale.id.equals(id) && scale.tet == tet) {
                    return scale;
                }
            }
        }
        List<ScaleDefinition> available = scalesForTet(tet);
        return available.isEmpty() ? ScaleDefinition.chromatic(tet)
                : available.get(0);
    }

    public List<ScaleDefinition> customScales() {
        return Collections.unmodifiableList(custom);
    }

    public ScaleDefinition saveCustom(String existingId, String name,
                                      int[] degrees) {
        String id = existingId != null && existingId.startsWith("user:")
                ? existingId : "user:" + UUID.randomUUID();
        ScaleDefinition scale = new ScaleDefinition(id, name, 12, degrees, false);
        boolean replaced = false;
        for (int i = 0; i < custom.size(); i++) {
            if (custom.get(i).id.equals(id)) {
                custom.set(i, scale);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            custom.add(scale);
        }
        save();
        return scale;
    }

    public boolean deleteCustom(String id) {
        for (int i = 0; i < custom.size(); i++) {
            if (custom.get(i).id.equals(id)) {
                custom.remove(i);
                save();
                return true;
            }
        }
        return false;
    }

    private void load() {
        custom.clear();
        String json = prefs.readString(PREF_KEY, "");
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String id = object.getString("id");
                if (!id.startsWith("user:")) {
                    continue;
                }
                JSONArray encoded = object.getJSONArray("degrees");
                int[] degrees = new int[encoded.length()];
                for (int d = 0; d < degrees.length; d++) {
                    degrees[d] = encoded.getInt(d);
                }
                try {
                    custom.add(new ScaleDefinition(id, object.getString("name"),
                            12, degrees, false));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (JSONException ignored) {
        }
    }

    private void save() {
        JSONArray array = new JSONArray();
        for (ScaleDefinition scale : custom) {
            try {
                JSONObject object = new JSONObject();
                object.put("id", scale.id);
                object.put("name", scale.name);
                JSONArray degrees = new JSONArray();
                for (int degree : scale.degrees) {
                    degrees.put(degree);
                }
                object.put("degrees", degrees);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        prefs.writeString(PREF_KEY, array.toString());
    }
}
