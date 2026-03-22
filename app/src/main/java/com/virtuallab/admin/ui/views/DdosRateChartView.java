package com.virtuallab.admin.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.sumit.virtuallabadmin.v28.R;
import com.virtuallab.admin.model.DdosRatePoint;

import java.util.ArrayList;
import java.util.List;

public final class DdosRateChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<DdosRatePoint> points = new ArrayList<>();

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
        int brand = ContextCompat.getColor(getContext(), R.color.brand);
        int muted = ContextCompat.getColor(getContext(), R.color.text_muted);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setColor(brand);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor((brand & 0x00FFFFFF) | 0x22000000);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor((muted & 0x00FFFFFF) | 0x33000000);
    }

    public void setPoints(@Nullable List<DdosRatePoint> next) {
        points.clear();
        if (next != null) points.addAll(next);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = dp(8);
        float left = pad;
        float top = pad;
        float right = w - pad;
        float bottom = h - pad;

        for (int i = 1; i <= 3; i++) {
            float y = top + ((bottom - top) * i / 4f);
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        if (points.size() < 2) return;

        int max = 1;
        for (DdosRatePoint p : points) {
            if (p != null) max = Math.max(max, p.count);
        }

        Path line = new Path();
        Path fill = new Path();

        for (int i = 0; i < points.size(); i++) {
            DdosRatePoint p = points.get(i);
            int c = p != null ? p.count : 0;
            float x = left + (right - left) * i / (points.size() - 1f);
            float y = bottom - (bottom - top) * (c / (float) max);
            if (i == 0) {
                line.moveTo(x, y);
                fill.moveTo(x, bottom);
                fill.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
        }
        fill.lineTo(right, bottom);
        fill.close();

        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}

