package org.bokontep.wavesynth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class Scope extends View {
    public static final int MODE_WAVE = 0;
    public static final int MODE_FFT = 1;
    public static final int MODE_BOTH = 2;

    private static final int ACCENT_GREEN = Color.rgb(92, 255, 192);
    private static final int ACCENT_RED = Color.rgb(255, 90, 110);

    private int accentColor = ACCENT_GREEN;
    private int mode = MODE_BOTH;
    private int xNoteScale = 160;

    private float[] data;
    private float[] spectrum = new float[0];
    private float[] spectrumPeaks = new float[0];
    private final float[] markerX = new float[10];
    private final float[] markerY = new float[10];
    private final String[] markerLabels = new String[10];
    private final boolean[] markerActive = new boolean[10];

    private final Paint tracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noteLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zoneLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrumPeakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path tracePath = new Path();
    private final Path fillPath = new Path();
    private int width;
    private int height;
    private float density;

    public interface NoteLabelProvider {
        String labelFor(int cell, int zone);
    }

    private NoteLabelProvider labelProvider;
    private int labelVersion;
    private int cachedLabelVersion = -1;
    private int cachedLabelWidth = -1;
    private int cachedLabelScale = -1;
    private String[] bottomLabels;
    private String[][] zoneLabels;

    public Scope(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setupPaints();
        setClipToOutline(true);
    }

    private void setupPaints() {
        tracePaint.setStyle(Paint.Style.STROKE);
        tracePaint.setStrokeWidth(dp(1.6f));
        tracePaint.setStrokeCap(Paint.Cap.ROUND);
        tracePaint.setStrokeJoin(Paint.Join.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(5.0f));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(0.7f));

        centerPaint.setStyle(Paint.Style.STROKE);
        centerPaint.setStrokeWidth(dp(0.9f));

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(1.4f));

        zonePaint.setStyle(Paint.Style.STROKE);
        zonePaint.setStrokeWidth(dp(0.9f));

        noteLabelPaint.setTextAlign(Paint.Align.CENTER);
        noteLabelPaint.setTypeface(Typeface.MONOSPACE);
        noteLabelPaint.setTextSize(dp(10.0f));
        noteLabelPaint.setAntiAlias(true);
        noteLabelPaint.setShadowLayer(dp(2.0f), 0.0f, 0.0f, 0xCC05080B);

        zoneLabelPaint.setTextAlign(Paint.Align.CENTER);
        zoneLabelPaint.setTypeface(Typeface.MONOSPACE);
        zoneLabelPaint.setTextSize(dp(9.0f));
        zoneLabelPaint.setAntiAlias(true);
        zoneLabelPaint.setShadowLayer(dp(1.8f), 0.0f, 0.0f, 0xAA05080B);

        spectrumPaint.setStyle(Paint.Style.FILL);

        spectrumPeakPaint.setStyle(Paint.Style.STROKE);
        spectrumPeakPaint.setStrokeWidth(dp(1.2f));
        spectrumPeakPaint.setStrokeCap(Paint.Cap.ROUND);

        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(dp(1.4f));

        markerFillPaint.setStyle(Paint.Style.FILL);

        markerTextPaint.setTextAlign(Paint.Align.CENTER);
        markerTextPaint.setTypeface(Typeface.MONOSPACE);
        markerTextPaint.setTextSize(dp(9.0f));
        markerTextPaint.setAntiAlias(true);

        applyAccent();
    }

    private void applyAccent() {
        tracePaint.setColor(accentColor);
        glowPaint.setColor(withAlpha(accentColor, 34));
        gridPaint.setColor(withAlpha(accentColor, 22));
        centerPaint.setColor(withAlpha(accentColor, 55));
        edgePaint.setColor(withAlpha(accentColor, 165));
        zonePaint.setColor(withAlpha(accentColor, 95));
        noteLabelPaint.setColor(withAlpha(accentColor, 220));
        zoneLabelPaint.setColor(withAlpha(accentColor, 175));
        spectrumPeakPaint.setColor(withAlpha(accentColor, 185));
        markerPaint.setColor(accentColor);
        markerFillPaint.setColor(withAlpha(accentColor, 28));
        markerTextPaint.setColor(accentColor);
        updateGradients();
    }

    private void updateGradients() {
        if (width <= 0 || height <= 0) {
            return;
        }
        fillPaint.setShader(new LinearGradient(0.0f, 0.0f, 0.0f, height,
                withAlpha(accentColor, 46), withAlpha(accentColor, 2),
                Shader.TileMode.CLAMP));
        spectrumPaint.setShader(new LinearGradient(0.0f, height, 0.0f, 0.0f,
                withAlpha(accentColor, 96), withAlpha(accentColor, 12),
                Shader.TileMode.CLAMP));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public void setRed(boolean red) {
        setAccentColor(red ? ACCENT_RED : ACCENT_GREEN);
    }

    public void setAccentColor(int color) {
        if (accentColor == color) {
            return;
        }
        accentColor = color;
        applyAccent();
        invalidate();
    }

    public void setXNoteScale(int xNoteScale) {
        if (this.xNoteScale != xNoteScale) {
            this.xNoteScale = xNoteScale;
            invalidateNoteLabels();
        }
    }

    public void setNoteLabelProvider(NoteLabelProvider provider) {
        labelProvider = provider;
        invalidateNoteLabels();
    }

    public void invalidateNoteLabels() {
        labelVersion++;
        invalidate();
    }

    public void setMode(int mode) {
        int clamped = Math.max(MODE_WAVE, Math.min(MODE_BOTH, mode));
        if (this.mode != clamped) {
            this.mode = clamped;
            invalidate();
        }
    }

    public void setData(float[] newData) {
        data = newData;
    }

    public void setSpectrum(float[] bars) {
        if (bars == null || bars.length == 0) {
            return;
        }
        if (spectrum.length != bars.length) {
            spectrum = new float[bars.length];
            spectrumPeaks = new float[bars.length];
        }
        for (int i = 0; i < bars.length; i++) {
            float value = Math.max(0.0f, Math.min(1.0f, bars[i]));
            spectrum[i] = value;
            if (value > spectrumPeaks[i]) {
                spectrumPeaks[i] = value;
            } else {
                spectrumPeaks[i] = Math.max(value, spectrumPeaks[i] * 0.94f);
            }
        }
    }

    public void setMarker(String id, float x, float y, String label) {
        int slot = markerSlot(id);
        if (slot < 0) {
            return;
        }
        markerX[slot] = x;
        markerY[slot] = y;
        markerLabels[slot] = label;
        markerActive[slot] = true;
        invalidate();
    }

    public void unsetMarker(String id) {
        int slot = markerSlot(id);
        if (slot < 0) {
            return;
        }
        markerActive[slot] = false;
        markerLabels[slot] = null;
        invalidate();
    }

    private static int markerSlot(String id) {
        if (id == null) {
            return -1;
        }
        try {
            int slot = Integer.parseInt(id);
            return slot >= 0 && slot < 10 ? slot : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        updateGradients();
        invalidateNoteLabels();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (width <= 0 || height <= 0) {
            return;
        }
        drawGrid(canvas);
        if (mode != MODE_WAVE) {
            drawSpectrum(canvas);
        }
        if (mode != MODE_FFT) {
            drawWaveform(canvas);
        }
        drawNoteLabels(canvas);
        drawMarkers(canvas);
    }

    private void drawGrid(Canvas canvas) {
        final float center = height / 2.0f;
        final float inset = dp(1.2f);
        canvas.drawRect(inset, inset, width - inset, height - inset, edgePaint);
        canvas.drawLine(0.0f, center, width, center, centerPaint);
        canvas.drawLine(0.0f, height / 3.0f, width, height / 3.0f, zonePaint);
        canvas.drawLine(0.0f, height * 2.0f / 3.0f, width,
                height * 2.0f / 3.0f, zonePaint);
        int step = Math.max(1, xNoteScale);
        for (int x = step; x < width; x += step) {
            canvas.drawLine(x, 0.0f, x, height, gridPaint);
        }
    }

    private void drawNoteLabels(Canvas canvas) {
        ensureNoteLabels();
        if (zoneLabels == null || bottomLabels == null
                || xNoteScale < dp(44.0f)) {
            return;
        }
        final float[] zoneY = {height * 5.0f / 6.0f,
                height / 2.0f + dp(16.0f), height / 6.0f};
        final float bottomY = height - dp(7.0f);
        final float zoneBaseline = zoneLabelPaint.getTextSize() / 3.0f;
        for (int cell = 0; cell < bottomLabels.length; cell++) {
            float cx = (cell + 0.5f) * xNoteScale;
            if (cx < dp(12.0f) || cx > width - dp(12.0f)) {
                continue;
            }
            if (bottomLabels[cell] != null) {
                canvas.drawText(bottomLabels[cell], cx, bottomY,
                        noteLabelPaint);
            }
            for (int zone = 0; zone < 3; zone++) {
                String label = zoneLabels[zone][cell];
                if (label != null) {
                    canvas.drawText(label, cx, zoneY[zone] + zoneBaseline,
                            zoneLabelPaint);
                }
            }
        }
    }

    private void ensureNoteLabels() {
        if (labelProvider == null || width <= 0) {
            zoneLabels = null;
            bottomLabels = null;
            return;
        }
        if (cachedLabelVersion == labelVersion
                && cachedLabelWidth == width
                && cachedLabelScale == xNoteScale) {
            return;
        }
        int columns = Math.max(1, (int) Math.ceil(width / (double) xNoteScale));
        String[] bottom = new String[columns];
        String[][] zones = new String[3][columns];
        for (int cell = 0; cell < columns; cell++) {
            for (int zone = 0; zone < 3; zone++) {
                zones[zone][cell] = labelProvider.labelFor(cell, zone);
            }
            bottom[cell] = zones[0][cell];
        }
        zoneLabels = zones;
        bottomLabels = bottom;
        cachedLabelVersion = labelVersion;
        cachedLabelWidth = width;
        cachedLabelScale = xNoteScale;
    }

    private void drawSpectrum(Canvas canvas) {
        if (spectrum.length == 0) {
            return;
        }
        final int count = spectrum.length;
        final float barWidth = width / (float) count;
        final float gap = Math.max(dp(0.5f), barWidth * 0.18f);
        final float bottom = height;
        final float usableHeight = height * 0.9f;
        for (int i = 0; i < count; i++) {
            float value = spectrum[i];
            if (value > 0.01f) {
                float barHeight = value * usableHeight;
                float left = i * barWidth + gap * 0.5f;
                float right = (i + 1) * barWidth - gap * 0.5f;
                canvas.drawRect(left, bottom - barHeight, right, bottom,
                        spectrumPaint);
            }
            float peak = spectrumPeaks[i];
            if (peak > 0.01f) {
                float peakY = bottom - peak * usableHeight;
                float left = i * barWidth + gap * 0.5f;
                float right = (i + 1) * barWidth - gap * 0.5f;
                canvas.drawLine(left, peakY, right, peakY, spectrumPeakPaint);
            }
        }
    }

    private void drawWaveform(Canvas canvas) {
        if (data == null || data.length < 2) {
            return;
        }
        final int length = data.length;
        final int start = findTrigger(data);
        final float center = height / 2.0f;
        final float amplitude = height * 0.42f;

        tracePath.reset();
        fillPath.reset();
        fillPath.moveTo(0.0f, center);

        for (int i = 0; i < length; i++) {
            float sample = data[(start + i) % length];
            sample = Math.max(-1.25f, Math.min(1.25f, sample));
            float x = width * (i / (float) (length - 1));
            float y = center - sample * amplitude;
            if (i == 0) {
                tracePath.moveTo(x, y);
            } else {
                tracePath.lineTo(x, y);
            }
            fillPath.lineTo(x, y);
        }
        fillPath.lineTo(width, center);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(tracePath, glowPaint);
        canvas.drawPath(tracePath, tracePaint);
    }

    private static int findTrigger(float[] samples) {
        final int limit = Math.max(2, samples.length / 4);
        for (int i = 1; i < limit; i++) {
            if (samples[i - 1] <= 0.0f && samples[i] > 0.0f) {
                return i;
            }
        }
        return 0;
    }

    private void drawMarkers(Canvas canvas) {
        final float radius = dp(16.0f);
        for (int i = 0; i < markerActive.length; i++) {
            if (!markerActive[i]) {
                continue;
            }
            float x = markerX[i];
            float y = markerY[i];
            canvas.drawCircle(x, y, radius, markerFillPaint);
            canvas.drawCircle(x, y, radius, markerPaint);
            if (markerLabels[i] != null && markerLabels[i].length() > 0) {
                canvas.drawText(markerLabels[i], x,
                        Math.max(dp(12.0f), y - radius - dp(5.0f)),
                        markerTextPaint);
            }
        }
    }

    private float dp(float value) {
        return value * density;
    }
}
