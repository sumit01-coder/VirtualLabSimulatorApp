package com.virtuallab.admin.ui.views;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;

public final class EdgeToEdge {
    private EdgeToEdge() {}

    public static void enable(@NonNull AppCompatActivity activity, @NonNull View view) {
        enable(activity, view, true, true);
    }

    public static void enable(
            @NonNull AppCompatActivity activity,
            @NonNull View view,
            boolean applyTop,
            boolean applyBottom
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        final int startPaddingLeft = view.getPaddingLeft();
        final int startPaddingTop = view.getPaddingTop();
        final int startPaddingRight = view.getPaddingRight();
        final int startPaddingBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int top = applyTop ? bars.top : 0;
            int bottom = applyBottom ? bars.bottom : 0;
            v.setPadding(
                    startPaddingLeft + bars.left,
                    startPaddingTop + top,
                    startPaddingRight + bars.right,
                    startPaddingBottom + bottom
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(view);
    }
}

