package org.bokontep.wavesynth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Compact pie/ring showing loop recording fill or playback position. */
public class LoopMeterView extends View {
    private static final int RECORD_COLOR = Color.rgb(255, 90, 110);
    private static final int PLAY_COLOR = Color.rgb(92, 255, 192);

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private boolean recording;
    private boolean playing;
    private boolean hasLoop;
    private float recordProgress;
    private float playProgress;
    private float density;

    public LoopMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.rgb(8, 14, 18));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.0f));
        ringPaint.setColor(Color.rgb(27, 44, 53));
        recordPaint.setStyle(Paint.Style.FILL);
        recordPaint.setColor(withAlpha(RECORD_COLOR, 185));
        playPaint.setStyle(Paint.Style.STROKE);
        playPaint.setStrokeWidth(dp(3.0f));
        playPaint.setStrokeCap(Paint.Cap.ROUND);
        playPaint.setColor(PLAY_COLOR);
        setContentDescription("Loop record and playback progress");
    }

    public void setLoopState(boolean recording, boolean playing, boolean hasLoop,
                             float recordProgress, float playProgress) {
        boolean changed = this.recording != recording
                || this.playing != playing
                || this.hasLoop != hasLoop
                || Math.abs(this.recordProgress - recordProgress) > 0.001f
                || Math.abs(this.playProgress - playProgress) > 0.001f;
        this.recording = recording;
        this.playing = playing;
        this.hasLoop = hasLoop;
        this.recordProgress = clamp(recordProgress);
        this.playProgress = clamp(playProgress);
        if (changed) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float cx = getWidth() / 2.0f;
        final float cy = getHeight() / 2.0f;
        final float radius = Math.max(1.0f,
                Math.min(getWidth(), getHeight()) / 2.0f - dp(2.0f));
        canvas.drawCircle(cx, cy, radius, backgroundPaint);
        canvas.drawCircle(cx, cy, radius - dp(0.5f), ringPaint);

        arcRect.set(cx - radius + dp(3.0f), cy - radius + dp(3.0f),
                cx + radius - dp(3.0f), cy + radius - dp(3.0f));

        if (recording && recordProgress > 0.0f) {
            canvas.drawArc(arcRect, -90.0f, recordProgress * 360.0f, true,
                    recordPaint);
        }
        if (playing && playProgress > 0.0f) {
            canvas.drawArc(arcRect, -90.0f, playProgress * 360.0f, false,
                    playPaint);
        }
        if (!recording && !playing && hasLoop) {
            Paint idle = ringPaint;
            idle.setColor(withAlpha(Color.rgb(143, 163, 173), 80));
            canvas.drawCircle(cx, cy, radius - dp(0.5f), idle);
            idle.setColor(Color.rgb(27, 44, 53));
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private float dp(float value) {
        return value * density;
    }
}
