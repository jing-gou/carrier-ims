package io.github.nku100.carrierims.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import io.github.nku100.carrierims.R;
import io.github.nku100.carrierims.config.Thresholds;

/**
 * A proportional strip of the five signal levels between {@link Thresholds#MIN_DBM} and
 * {@link Thresholds#MAX_DBM}, with the four configured boundaries marked underneath.
 *
 * <p>Width of each band is proportional to its dBm span, so moving a threshold visibly moves the
 * boundary instead of only changing a number.
 */
public final class SignalBandView extends View {

    private static final float BAND_HEIGHT_DP = 26f;
    private static final float CORNER_DP = 7f;
    private static final float TICK_DP = 6f;
    private static final float LABEL_GAP_DP = 7f;

    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bandRect = new RectF();
    private final Path clipPath = new Path();
    /** Reused every frame: onDraw must not allocate. */
    private final float[] bounds = new float[6];
    private final String[] labels = new String[4];

    private final int[] bandColors;
    private final int mutedColor;
    private final float density;

    private int[] thresholds;

    public SignalBandView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        bandColors = new int[]{
                context.getColor(R.color.level_none),
                context.getColor(R.color.level_poor),
                context.getColor(R.color.level_moderate),
                context.getColor(R.color.level_good),
                context.getColor(R.color.level_excellent),
        };
        mutedColor = context.getColor(R.color.text_muted);

        tickPaint.setColor(mutedColor);
        tickPaint.setStrokeWidth(dp(1.5f));
        tickPaint.setStyle(Paint.Style.STROKE);

        labelPaint.setColor(mutedColor);
        labelPaint.setTextSize(dp(10f));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        emptyPaint.setColor(context.getColor(R.color.surface_stroke));
        emptyPaint.setStyle(Paint.Style.FILL);
    }

    /** @param values four ascending dBm thresholds, or {@code null} to render an empty state */
    public void setThresholds(int[] values) {
        this.thresholds = values == null ? null : values.clone();
        if (this.thresholds != null) {
            for (int i = 0; i < 4; i++) {
                labels[i] = String.valueOf(this.thresholds[i]);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        if (right <= left) {
            return;
        }
        float top = getPaddingTop();
        float bottom = top + dp(BAND_HEIGHT_DP);

        bandRect.set(left, top, right, bottom);
        float radius = dp(CORNER_DP);
        clipPath.reset();
        clipPath.addRoundRect(bandRect, radius, radius, Path.Direction.CW);

        if (thresholds == null) {
            canvas.drawRoundRect(bandRect, radius, radius, emptyPaint);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("—", (left + right) / 2f, bottom + dp(LABEL_GAP_DP + 11f), labelPaint);
            return;
        }

        bounds[0] = Thresholds.MIN_DBM;
        bounds[1] = thresholds[0];
        bounds[2] = thresholds[1];
        bounds[3] = thresholds[2];
        bounds[4] = thresholds[3];
        bounds[5] = Thresholds.MAX_DBM;
        float span = Thresholds.MAX_DBM - Thresholds.MIN_DBM;

        canvas.save();
        canvas.clipPath(clipPath);
        bandPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < bandColors.length; i++) {
            float x0 = left + (bounds[i] - Thresholds.MIN_DBM) / span * (right - left);
            float x1 = left + (bounds[i + 1] - Thresholds.MIN_DBM) / span * (right - left);
            if (x1 <= x0) {
                continue;
            }
            bandPaint.setColor(bandColors[i]);
            canvas.drawRect(x0, top, x1, bottom, bandPaint);
        }
        canvas.restore();

        float tickBottom = bottom + dp(TICK_DP);
        float labelBaseline = tickBottom + dp(LABEL_GAP_DP) + labelPaint.getTextSize();
        for (int i = 0; i < 4; i++) {
            float x = left + (thresholds[i] - Thresholds.MIN_DBM) / span * (right - left);
            canvas.drawLine(x, bottom, x, tickBottom, tickPaint);

            String label = labels[i];
            float half = labelPaint.measureText(label) / 2f;
            float clamped = Math.max(left + half, Math.min(right - half, x));
            canvas.drawText(label, clamped, labelBaseline, labelPaint);
        }
    }

    private float dp(float value) {
        return value * density;
    }
}
