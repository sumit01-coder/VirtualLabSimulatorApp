package com.virtuallab.admin.ui;

import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.virtuallab.admin.R;
import com.virtuallab.admin.security.AppLockPrefs;

import java.util.concurrent.Executor;

public final class AppLockActivity extends AppCompatActivity {
    private boolean started = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock);
        com.virtuallab.admin.ui.views.EdgeToEdge.enable(this, findViewById(R.id.root));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (started) return;
        started = true;
        authenticate();
    }

    private void authenticate() {
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

        int can = BiometricManager.from(this).canAuthenticate(authenticators);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Enable screen lock / biometrics to use App Lock", Toast.LENGTH_LONG).show();
            finishAffinity();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                AppLockPrefs.markUnlockedNow(AppLockActivity.this);
                finish();
                overridePendingTransition(0, 0);
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                // If user cancels, close app.
                finishAffinity();
            }

            @Override
            public void onAuthenticationFailed() {
                // Keep prompt open.
            }
        });

        BiometricPrompt.PromptInfo.Builder b = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Virtual Lab Admin")
                .setSubtitle("Verify to continue");

        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(authenticators);
        } else {
            // For older devices, allow device credentials.
            b.setDeviceCredentialAllowed(true);
        }

        prompt.authenticate(b.build());
    }
}

