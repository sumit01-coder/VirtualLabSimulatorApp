package com.virtuallab.admin.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

public final class CurvedBottomNavBackgroundView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shapePath = new Path();

    private final float cornerRadius;
    private final float sideInset;
    private final float topInset;
    private final float bottomInset;
    private final float notchRadius;
    private final float notchDepth;

    public CurvedBottomNavBackgroundView(Context context) {
        this(context, null);
    }

    public CurvedBottomNavBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        cornerRadius = dp(30f);
        sideInset = dp(4f);
        topInset = dp(18f);
        bottomInset = dp(12f);
        notchRadius = dp(38f);
        notchDepth = dp(30f);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setShadowLayer(dp(24f), 0f, dp(10f), Color.parseColor("#140F172A"));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1f));
        strokePaint.setColor(Color.parseColor("#0D94A3B8"));

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dp(1.2f));
        highlightPaint.setColor(Color.parseColor("#33FFFFFF"));

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) return;

        float left = sideInset;
        float right = width - sideInset;
        float top = topInset;
        float bottom = height - bottomInset;
        float centerX = width / 2f;
        float notchStart = centerX - notchRadius;
        float notchEnd = centerX + notchRadius;

        shapePath.reset();
        shapePath.moveTo(left + cornerRadius, top);
        shapePath.lineTo(notchStart - dp(10f), top);
        shapePath.cubicTo(
                centerX - dp(42f), top,
                centerX - dp(28f), top + notchDepth,
                centerX, top + notchDepth
        );
        shapePath.cubicTo(
                centerX + dp(28f), top + notchDepth,
                centerX + dp(42f), top,
                notchEnd + dp(10f), top
        );
        shapePath.lineTo(right - cornerRadius, top);
        shapePath.quadTo(right, top, right, top + cornerRadius);
        shapePath.lineTo(right, bottom - cornerRadius);
        shapePath.quadTo(right, bottom, right - cornerRadius, bottom);
        shapePath.lineTo(left + cornerRadius, bottom);
        shapePath.quadTo(left, bottom, left, bottom - cornerRadius);
        shapePath.lineTo(left, top + cornerRadius);
        shapePath.quadTo(left, top, left + cornerRadius, top);
        shapePath.close();

        fillPaint.setShader(new LinearGradient(
                0f,
                top,
                0f,
                bottom,
                Color.parseColor("#FCFFFFFF"),
                Color.parseColor("#FFF8FBFF"),
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(shapePath, fillPaint);
        canvas.drawPath(shapePath, strokePaint);

        RectF highlightRect = new RectF(left + dp(1f), top + dp(1f), right - dp(1f), bottom - dp(18f));
        canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, highlightPaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
