package org.bokontep.wavesynth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Compact live indicator for the motion modulation sensors. */
public final class SensorIndicatorView extends View {
    private static final int ACCEL_COLOR = Color.rgb(92, 255, 192);
    private static final int GYRO_COLOR = Color.rgb(255, 179, 92);
    private static final int MAG_COLOR = Color.rgb(122, 184, 255);

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gyroPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint magPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean active;
    private int mask;
    private float value;
    private float density;

    public SensorIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.0f));
        ringPaint.setColor(Color.rgb(27, 44, 53));
        accelPaint.setStyle(Paint.Style.FILL);
        accelPaint.setColor(ACCEL_COLOR);
        gyroPaint.setStyle(Paint.Style.FILL);
        gyroPaint.setColor(GYRO_COLOR);
        magPaint.setStyle(Paint.Style.FILL);
        magPaint.setColor(MAG_COLOR);
        valuePaint.setStyle(Paint.Style.STROKE);
        valuePaint.setStrokeWidth(dp(2.0f));
        valuePaint.setStrokeCap(Paint.Cap.ROUND);
        valuePaint.setColor(0xFFE6F1F5);
        setContentDescription("Motion sensors off");
    }

    public void setState(boolean active, int mask, float value) {
        this.active = active;
        this.mask = mask & 0x7;
        this.value = Math.max(-1.0f, Math.min(1.0f, value));
        StringBuilder description = new StringBuilder(
                active ? "Motion sensors active" : "Motion sensors off");
        if (active) {
            if ((mask & 1) != 0) description.append(", accelerometer");
            if ((mask & 2) != 0) description.append(", gyroscope");
            if ((mask & 4) != 0) description.append(", magnetometer");
            description.append(String.format(java.util.Locale.US,
                    ", value %+.2f", this.value));
        }
        setContentDescription(description.toString());
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float cx = getWidth() / 2.0f;
        final float cy = getHeight() / 2.0f;
        final float radius = Math.max(1.0f,
                Math.min(getWidth(), getHeight()) / 2.0f - dp(1.5f));
        canvas.drawCircle(cx, cy, radius, ringPaint);

        final float dotRadius = Math.max(dp(1.5f), radius * 0.15f);
        final float orbit = radius * 0.62f;
        drawDot(canvas, cx, cy - orbit, dotRadius, accelPaint, (mask & 1) != 0);
        drawDot(canvas, cx - orbit * 0.86f, cy + orbit * 0.5f, dotRadius,
                gyroPaint, (mask & 2) != 0);
        drawDot(canvas, cx + orbit * 0.86f, cy + orbit * 0.5f, dotRadius,
                magPaint, (mask & 4) != 0);

        if (active) {
            final float dx = value * radius * 0.58f;
            valuePaint.setColor(0xFFE6F1F5);
            canvas.drawLine(cx, cy, cx + dx, cy, valuePaint);
            valuePaint.setColor(0xFF5CFFC0);
            canvas.drawCircle(cx + dx, cy, dp(1.6f), valuePaint);
        }
    }

    private void drawDot(Canvas canvas, float x, float y, float radius,
                         Paint paint, boolean on) {
        int color = paint.getColor();
        paint.setColor(on ? color : Color.rgb(35, 49, 58));
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(color);
    }

    private float dp(float value) {
        return value * density;
    }
}
