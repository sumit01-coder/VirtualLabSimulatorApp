package com.virtuallab.admin.ui.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.sumit.virtuallabadmin.v29.R;

public class CustomAlertUtils {

    public interface OnAlertAction {
        void onAction();
    }

    public static void showConfirmation(Context context, String title, String message, String positiveText, String negativeText, int iconResId, int iconTintResId, OnAlertAction onConfirm) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_custom_alert, null);

        ImageView alertIcon = view.findViewById(R.id.alertIcon);
        TextView alertTitle = view.findViewById(R.id.alertTitle);
        TextView alertMessage = view.findViewById(R.id.alertMessage);
        MaterialButton btnNegative = view.findViewById(R.id.btnNegative);
        MaterialButton btnPositive = view.findViewById(R.id.btnPositive);

        if (iconResId != 0) {
            alertIcon.setImageResource(iconResId);
        }
        if (iconTintResId != 0) {
            alertIcon.setColorFilter(context.getResources().getColor(iconTintResId, context.getTheme()));
        }

        alertTitle.setText(title);
        alertMessage.setText(message);

        if (negativeText != null && !negativeText.isEmpty()) {
            btnNegative.setText(negativeText);
            btnNegative.setVisibility(View.VISIBLE);
        } else {
            btnNegative.setVisibility(View.GONE);
        }

        if (positiveText != null && !positiveText.isEmpty()) {
            btnPositive.setText(positiveText);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnNegative.setOnClickListener(v -> dialog.dismiss());
        btnPositive.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.onAction();
            }
        });

        dialog.show();
    }
    
    public static void showWarning(Context context, String title, String message, String positiveText, OnAlertAction onConfirm) {
        showConfirmation(context, title, message, positiveText, "Cancel", R.drawable.ic_warning, R.color.error, onConfirm);
    }
}
