package org.bokontep.wavesynth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class WaveDisplay extends View {
    private static final int ACCENT_GREEN = Color.rgb(92, 255, 192);
    private static final int ACCENT_RED = Color.rgb(255, 90, 110);

    private int foregroundColor = ACCENT_GREEN;
    private float[] data;
    private int width;
    private int height;
    private float density;

    private final Paint tracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tracePath = new Path();
    private final Path fillPath = new Path();

    public WaveDisplay(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setupPaint();
        setClipToOutline(true);
    }

    private void setupPaint() {
        tracePaint.setStyle(Paint.Style.STROKE);
        tracePaint.setStrokeWidth(dp(1.5f));
        tracePaint.setStrokeCap(Paint.Cap.ROUND);
        tracePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        centerPaint.setStyle(Paint.Style.STROKE);
        centerPaint.setStrokeWidth(dp(0.8f));

        applyAccent();
    }

    private void applyAccent() {
        tracePaint.setColor(foregroundColor);
        centerPaint.setColor(withAlpha(foregroundColor, 55));
        updateGradient();
    }

    private void updateGradient() {
        if (height <= 0) {
            return;
        }
        fillPaint.setShader(new LinearGradient(0.0f, 0.0f, 0.0f, height,
                withAlpha(foregroundColor, 62), withAlpha(foregroundColor, 3),
                Shader.TileMode.CLAMP));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public void setData(float[] newData) {
        data = newData;
        invalidate();
    }

    public void setRed(boolean red) {
        foregroundColor = red ? ACCENT_RED : ACCENT_GREEN;
        applyAccent();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        updateGradient();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (width <= 0 || height <= 0) {
            return;
        }
        final float center = height / 2.0f;
        canvas.drawLine(0.0f, center, width, center, centerPaint);
        if (data == null || data.length < 2) {
            return;
        }

        tracePath.reset();
        fillPath.reset();
        fillPath.moveTo(0.0f, center);
        final float amplitude = height * 0.42f;
        for (int i = 0; i < data.length; i++) {
            float sample = Math.max(-1.0f, Math.min(1.0f, data[i]));
            float x = width * (i / (float) (data.length - 1));
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
        canvas.drawPath(tracePath, tracePaint);
    }

    private float dp(float value) {
        return value * density;
    }
}
