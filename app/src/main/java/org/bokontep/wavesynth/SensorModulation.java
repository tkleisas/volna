package org.bokontep.wavesynth;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Normalizes phone motion/magnetic sensors for the modulation matrix. */
public final class SensorModulation implements SensorEventListener {
    public static final int SOURCE_COUNT = 10;
    public static final int ACCEL_X = 0;
    public static final int ACCEL_Y = 1;
    public static final int ACCEL_Z = 2;
    public static final int GYRO_X = 3;
    public static final int GYRO_Y = 4;
    public static final int GYRO_Z = 5;
    public static final int MAG_X = 6;
    public static final int MAG_Y = 7;
    public static final int MAG_Z = 8;
    public static final int MAG_MAGNITUDE = 9;

    public interface Listener {
        void onModSource(int source, float value);
    }

    private static final String[] SOURCE_NAMES = {
            "Accel X", "Accel Y", "Accel Z",
            "Gyro X", "Gyro Y", "Gyro Z",
            "Mag X", "Mag Y", "Mag Z", "Mag magnitude"
    };

    private final SensorManager manager;
    private final Sensor accelerometer;
    private final Sensor gyroscope;
    private final Sensor magnetometer;
    private final Listener listener;
    private final boolean[] required = new boolean[SOURCE_COUNT];
    private final float[] raw = new float[SOURCE_COUNT];
    private final float[] baseline = new float[SOURCE_COUNT];
    private final float[] smoothed = new float[SOURCE_COUNT];
    private final boolean[] hasBaseline = new boolean[SOURCE_COUNT];
    private boolean running;
    private boolean needAccelerometer;
    private boolean needGyroscope;
    private boolean needMagnetometer;
    private int baselineMask = 0x7;

    public SensorModulation(Context context, Listener listener) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = manager != null
                ? manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        gyroscope = manager != null
                ? manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        magnetometer = manager != null
                ? manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) : null;
        this.listener = listener;
    }

    public static String sourceName(int source) {
        if (source < 0 || source >= SOURCE_NAMES.length) {
            return "None";
        }
        return SOURCE_NAMES[source];
    }

    public static boolean isAccelerometerSource(int source) {
        return source >= ACCEL_X && source <= ACCEL_Z;
    }

    public static boolean isGyroSource(int source) {
        return source >= GYRO_X && source <= GYRO_Z;
    }

    public static boolean isMagnetometerSource(int source) {
        return source >= MAG_X && source <= MAG_MAGNITUDE;
    }

    public void setRequiredSources(boolean[] sourceRequired) {
        boolean accel = false;
        boolean gyro = false;
        boolean mag = false;
        for (int i = 0; i < SOURCE_COUNT; i++) {
            required[i] = sourceRequired != null
                    && i < sourceRequired.length && sourceRequired[i];
            if (!required[i]) {
                continue;
            }
            if (isAccelerometerSource(i)) {
                accel = true;
            } else if (isGyroSource(i)) {
                gyro = true;
            } else {
                mag = true;
            }
        }
        boolean changed = needAccelerometer != accel
                || needGyroscope != gyro
                || needMagnetometer != mag;
        needAccelerometer = accel;
        needGyroscope = gyro;
        needMagnetometer = mag;
        if (running && changed) {
            stopInternal();
            startInternal();
        }
    }

    public boolean start() {
        if (!running) {
            startInternal();
        }
        return running;
    }

    public void stop() {
        if (running) {
            stopInternal();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean needsSensors() {
        for (boolean value : required) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRequiredSensors() {
        return (!needAccelerometer || accelerometer != null)
                && (!needGyroscope || gyroscope != null)
                && (!needMagnetometer || magnetometer != null);
    }

    public String statusText() {
        if (!needsSensors()) {
            return "Sensors off";
        }
        StringBuilder text = new StringBuilder();
        if (needAccelerometer) {
            text.append("Accel");
        }
        if (needGyroscope) {
            if (text.length() > 0) text.append(" + ");
            text.append("Gyro");
        }
        if (needMagnetometer) {
            if (text.length() > 0) text.append(" + ");
            text.append("Mag");
        }
        if (!hasRequiredSensors()) {
            text.append(" (unavailable)");
        }
        return text.toString();
    }

    public void calibrate() {
        baselineMask = 0x7;
        for (int i = 0; i < SOURCE_COUNT; i++) {
            hasBaseline[i] = false;
        }
    }

    private void startInternal() {
        if (manager == null || !needsSensors()) {
            running = false;
            return;
        }
        if (needAccelerometer && accelerometer != null) {
            manager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        if (needGyroscope && gyroscope != null) {
            manager.registerListener(this, gyroscope,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        if (needMagnetometer && magnetometer != null) {
            manager.registerListener(this, magnetometer,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        running = (needAccelerometer && accelerometer != null)
                || (needGyroscope && gyroscope != null)
                || (needMagnetometer && magnetometer != null);
    }

    private void stopInternal() {
        if (manager != null) {
            manager.unregisterListener(this);
        }
        running = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null) {
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            raw[ACCEL_X] = event.values[0] / SensorManager.GRAVITY_EARTH;
            raw[ACCEL_Y] = event.values[1] / SensorManager.GRAVITY_EARTH;
            raw[ACCEL_Z] = event.values[2] / SensorManager.GRAVITY_EARTH;
            calibrateGroup(ACCEL_X, 3);
            publish(ACCEL_X, 3);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            raw[GYRO_X] = event.values[0] / 5.0f;
            raw[GYRO_Y] = event.values[1] / 5.0f;
            raw[GYRO_Z] = event.values[2] / 5.0f;
            calibrateGroup(GYRO_X, 3);
            publish(GYRO_X, 3);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            raw[MAG_X] = event.values[0] / 100.0f;
            raw[MAG_Y] = event.values[1] / 100.0f;
            raw[MAG_Z] = event.values[2] / 100.0f;
            raw[MAG_MAGNITUDE] = (float) Math.sqrt(
                    raw[MAG_X] * raw[MAG_X] + raw[MAG_Y] * raw[MAG_Y]
                            + raw[MAG_Z] * raw[MAG_Z]);
            calibrateGroup(MAG_X, 3);
            if (!hasBaseline[MAG_MAGNITUDE]) {
                baseline[MAG_MAGNITUDE] = raw[MAG_MAGNITUDE];
                hasBaseline[MAG_MAGNITUDE] = true;
                smoothed[MAG_MAGNITUDE] = 0.0f;
            }
            publish(MAG_X, 3);
            publish(MAG_MAGNITUDE, 1);
        }
    }

    private void calibrateGroup(int first, int count) {
        int bit = first == ACCEL_X ? 1 : (first == GYRO_X ? 2 : 4);
        boolean force = (baselineMask & bit) != 0;
        if (!force && hasBaseline[first]) {
            return;
        }
        for (int i = first; i < first + count; i++) {
            baseline[i] = raw[i];
            hasBaseline[i] = true;
            smoothed[i] = 0.0f;
        }
        if (first == MAG_X) {
            baseline[MAG_MAGNITUDE] = raw[MAG_MAGNITUDE];
            hasBaseline[MAG_MAGNITUDE] = true;
            smoothed[MAG_MAGNITUDE] = 0.0f;
        }
        baselineMask &= ~bit;
    }

    private void publish(int first, int count) {
        for (int i = first; i < first + count; i++) {
            if (!required[i] || !hasBaseline[i]) {
                continue;
            }
            float value = raw[i] - baseline[i];
            value = Math.max(-1.0f, Math.min(1.0f, value));
            smoothed[i] += 0.25f * (value - smoothed[i]);
            listener.onModSource(i, smoothed[i]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
