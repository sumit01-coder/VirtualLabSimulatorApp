package com.virtuallab.admin.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.model.DdosRatePoint;

import java.util.ArrayList;
import java.util.List;

public final class DdosRateChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<DdosRatePoint> points = new ArrayList<>();
    private float drawProgress = 1f;

    public DdosRateChartView(Context context) {
        super(context);
        init();
    }

    public DdosRateChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DdosRateChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int muted = ContextCompat.getColor(getContext(), R.color.ddos_text_soft);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(3));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor((muted & 0x00FFFFFF) | 0x22000000);

        peakPaint.setStyle(Paint.Style.FILL);
        peakPaint.setColor(ContextCompat.getColor(getContext(), R.color.ddos_red));

        labelPaint.setColor(muted);
        labelPaint.setTextSize(dp(10));
        labelPaint.setFakeBoldText(true);
    }

    public void setPoints(@Nullable List<DdosRatePoint> next) {
        points.clear();
        if (next != null) {
            points.addAll(next);
        }
        drawProgress = 0f;
        animate().cancel();
        animate()
                .setDuration(450L)
                .setUpdateListener(animation -> {
                    drawProgress = animation.getAnimatedFraction();
                    invalidate();
                })
                .start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float pad = dp(8);
        float left = pad;
        float top = pad + dp(10);
        float right = w - pad;
        float bottom = h - pad - dp(14);

        for (int i = 1; i <= 3; i++) {
            float y = top + ((bottom - top) * i / 4f);
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        canvas.drawText("LOW", left, bottom + dp(12), labelPaint);
        canvas.drawText("PEAK", right - dp(28), top - dp(2), labelPaint);

        if (points.size() < 2) {
            return;
        }

        int max = 1;
        int peakIndex = 0;
        for (int i = 0; i < points.size(); i++) {
            DdosRatePoint point = points.get(i);
            if (point != null && point.count >= max) {
                max = point.count;
                peakIndex = i;
            }
        }

        int visibleCount = Math.max(2, Math.round((points.size() - 1) * drawProgress) + 1);
        Path line = new Path();
        Path fill = new Path();
        float peakX = left;
        float peakY = bottom;

        for (int i = 0; i < visibleCount; i++) {
            DdosRatePoint point = points.get(i);
            int count = point != null ? point.count : 0;
            float x = left + (right - left) * i / (points.size() - 1f);
            float y = bottom - (bottom - top) * (count / (float) max);
            if (i == 0) {
                line.moveTo(x, y);
                fill.moveTo(x, bottom);
                fill.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
            if (i == peakIndex) {
                peakX = x;
                peakY = y;
            }
        }

        float visibleRight = left + (right - left) * (visibleCount - 1) / (points.size() - 1f);
        fill.lineTo(visibleRight, bottom);
        fill.close();

        fillPaint.setShader(new LinearGradient(
                0f,
                top,
                0f,
                bottom,
                new int[]{
                        0x66F43F5E,
                        0x333B82F6,
                        0x00000000
                },
                null,
                Shader.TileMode.CLAMP
        ));
        linePaint.setShader(new LinearGradient(
                left,
                top,
                right,
                bottom,
                ContextCompat.getColor(getContext(), R.color.ddos_green),
                ContextCompat.getColor(getContext(), R.color.ddos_red),
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);
        canvas.drawCircle(peakX, peakY, dp(4), peakPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
