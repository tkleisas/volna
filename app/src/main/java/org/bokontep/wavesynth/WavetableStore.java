package org.bokontep.wavesynth;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Persists the imported wavetable in the app's private storage. */
public final class WavetableStore {
    private static final String FILE_NAME = "custom-wavetable.vwt";
    private static final String PREF_NAME = "customWavetableName";
    private static final String PREF_SOURCE = "customWavetableSource";

    private final Context context;
    private final AppPreferences preferences;

    public WavetableStore(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = new AppPreferences(this.context);
    }

    public byte[] load() {
        final File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile() || file.length() <= 0 ||
                file.length() > VwtWavetable.MAX_FILE_BYTES) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            return readFully(in, (int) file.length());
        } catch (IOException e) {
            return null;
        }
    }

    public String displayName() {
        return preferences.readString(PREF_NAME, "");
    }

    public String sourceName() {
        return preferences.readString(PREF_SOURCE, "");
    }

    public void save(byte[] bytes, String displayName, String sourceName)
            throws IOException {
        final File target = new File(context.getFilesDir(), FILE_NAME);
        final File temporary = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (!temporary.renameTo(target)) {
            if (!target.delete() || !temporary.renameTo(target)) {
                temporary.delete();
                throw new IOException("Could not store the wavetable");
            }
        }
        preferences.writeString(PREF_NAME, displayName);
        preferences.writeString(PREF_SOURCE, sourceName == null ? "" : sourceName);
    }

    public void clear() {
        final File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists()) {
            file.delete();
        }
        preferences.writeString(PREF_NAME, "");
        preferences.writeString(PREF_SOURCE, "");
    }

    public boolean hasStoredWavetable() {
        final File file = new File(context.getFilesDir(), FILE_NAME);
        return file.isFile() && file.length() > 0;
    }

    public String filePath() {
        return new File(context.getFilesDir(), FILE_NAME).getAbsolutePath();
    }

    private static byte[] readFully(FileInputStream in, int size) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        final byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
