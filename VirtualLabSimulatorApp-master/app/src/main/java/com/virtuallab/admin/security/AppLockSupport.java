package com.virtuallab.admin.security;

import android.content.Context;

import androidx.biometric.BiometricManager;

public final class AppLockSupport {
    private AppLockSupport() {}

    public static boolean canUseDeviceUnlock(Context context) {
        if (context == null) return false;
        int can = BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );
        return can == BiometricManager.BIOMETRIC_SUCCESS;
    }
}

