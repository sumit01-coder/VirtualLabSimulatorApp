package com.virtuallab.admin.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.feature.AuditLog;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.LoginRequest;
import com.virtuallab.admin.model.LoginResponseData;
import com.virtuallab.admin.model.VerifyOtpRequest;
import com.virtuallab.admin.security.AppLockPrefs;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class LoginActivity extends AppCompatActivity {
    private static final String PREFS_LOGIN = "login_ui_prefs";
    private static final String KEY_REMEMBER = "remember_username";
    private static final String KEY_USERNAME = "saved_username";

    private TokenStore tokenStore;
    private ApiService api;
    private SharedPreferences loginPrefs;

    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputEditText otpInput;
    private Button loginButton;
    private Button verifyOtpButton;
    private MaterialButton resendOtpButton;
    private MaterialCheckBox rememberCheckbox;
    private TextView forgotPasswordButton;
    private TextView otpHint;
    private ProgressBar progress;
    private View optionsRow;
    
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout otpLayout;

    private String tempToken = "";
    private String currentUsername = "";
    private String currentPassword = "";
    private CountDownTimer resendTimer;

    @Override
    protected void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        com.virtuallab.admin.ui.views.EdgeToEdge.enable(this, findViewById(R.id.root));

        tokenStore = new TokenStore(this);
        loginPrefs = getSharedPreferences(PREFS_LOGIN, MODE_PRIVATE);
        api = ApiClient.get(tokenStore);

        if (tokenStore.hasToken()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        otpInput = findViewById(R.id.otpInput);
        loginButton = findViewById(R.id.loginButton);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        resendOtpButton = findViewById(R.id.resendOtpButton);
        rememberCheckbox = findViewById(R.id.rememberCheckbox);
        forgotPasswordButton = findViewById(R.id.forgotPasswordButton);
        otpHint = findViewById(R.id.otpHint);
        progress = findViewById(R.id.progress);
        optionsRow = findViewById(R.id.optionsRow);
        usernameLayout = findViewById(R.id.usernameLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        otpLayout = findViewById(R.id.otpLayout);

        loginButton.setOnClickListener(v -> doLogin());
        verifyOtpButton.setOnClickListener(v -> doVerifyOtp());
        resendOtpButton.setOnClickListener(v -> doResendOtp());
        forgotPasswordButton.setOnClickListener(v -> Toast.makeText(this, "Please contact the super admin to reset your password", Toast.LENGTH_SHORT).show());

        showLoginMode();
        restoreRememberedUsername();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        verifyOtpButton.setEnabled(!loading);
        resendOtpButton.setEnabled(!loading && (resendTimer == null));
        if (loginButton instanceof MaterialButton) {
            ((MaterialButton) loginButton).setText(loading ? "Signing In..." : "Sign In");
        }
        if (verifyOtpButton instanceof MaterialButton) {
            ((MaterialButton) verifyOtpButton).setText(loading ? "Verifying..." : "Verify OTP");
        }
    }

    private void doLogin() {
        String username = usernameInput.getText() != null ? usernameInput.getText().toString().trim() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";

        if (username.isEmpty() || password.isEmpty()) {
            usernameLayout.setError(username.isEmpty() ? "Enter your email or username" : null);
            passwordLayout.setError(password.isEmpty() ? "Enter your password" : null);
            Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        usernameLayout.setError(null);
        passwordLayout.setError(null);
        persistRememberPreference(username);

        setLoading(true);

        api.login(new LoginRequest(username, password)).enqueue(new Callback<ApiResponse<LoginResponseData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponseData>> call, Response<ApiResponse<LoginResponseData>> response) {
                setLoading(false);
                if (!response.isSuccessful()) { Toast.makeText(LoginActivity.this, parseError(response), Toast.LENGTH_SHORT).show(); return; } if (response.body() == null) { Toast.makeText(LoginActivity.this, "Empty response", Toast.LENGTH_SHORT).show(); return; }

                ApiResponse<LoginResponseData> body = response.body();
                if (!body.status || body.data == null) {
                    Toast.makeText(LoginActivity.this, body.message != null ? body.message : "Login failed", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (body.data.require_2fa) {
                     tempToken = body.data.temp_token;
                     currentUsername = username;
                     currentPassword = password;

                     String masked = body.data.masked_email != null && !body.data.masked_email.trim().isEmpty()
                             ? body.data.masked_email.trim()
                             : "your admin email";
                     showOtpMode(masked);

                     int cooldown = body.data.resend_cooldown > 0 ? body.data.resend_cooldown : 30;
                     startResendCooldown(cooldown);
                     Toast.makeText(LoginActivity.this, "OTP sent to your admin email", Toast.LENGTH_SHORT).show();
                } else {
                     if (body.data.token == null) {
                         Toast.makeText(LoginActivity.this, "Missing token", Toast.LENGTH_SHORT).show();
                         return;
                     }
                     saveTokenAndStartMain(body.data, username);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResponseData>> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doResendOtp() {
        if (currentUsername.trim().isEmpty() || currentPassword.isEmpty()) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            resetToLogin();
            return;
        }

        setLoading(true);
        api.login(new LoginRequest(currentUsername, currentPassword)).enqueue(new Callback<ApiResponse<LoginResponseData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponseData>> call, Response<ApiResponse<LoginResponseData>> response) {
                setLoading(false);
                if (!response.isSuccessful()) { Toast.makeText(LoginActivity.this, parseError(response), Toast.LENGTH_SHORT).show(); return; } if (response.body() == null || !response.body().status || response.body().data == null) { Toast.makeText(LoginActivity.this, "Resend failed", Toast.LENGTH_SHORT).show(); return; }

                LoginResponseData d = response.body().data;
                if (!d.require_2fa || d.temp_token == null || d.temp_token.trim().isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Resend failed", Toast.LENGTH_SHORT).show();
                    return;
                }

                tempToken = d.temp_token;
                String masked = d.masked_email != null && !d.masked_email.trim().isEmpty() ? d.masked_email.trim() : "your admin email";
                otpHint.setText("New code sent to " + masked + ".");

                int cooldown = d.resend_cooldown > 0 ? d.resend_cooldown : 30;
                startResendCooldown(cooldown);
                Toast.makeText(LoginActivity.this, "OTP resent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResponseData>> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doVerifyOtp() {
        String otp = otpInput.getText() != null ? otpInput.getText().toString().trim() : "";
        if (otp.length() != 6) {
            otpLayout.setError("Enter a valid 6-digit OTP");
            Toast.makeText(this, "Enter 6-digit OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        otpLayout.setError(null);

        setLoading(true);
        api.verifyOtp(new VerifyOtpRequest(currentUsername, tempToken, otp)).enqueue(new Callback<ApiResponse<LoginResponseData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponseData>> call, Response<ApiResponse<LoginResponseData>> response) {
                setLoading(false);
                if (!response.isSuccessful()) { Toast.makeText(LoginActivity.this, parseError(response), Toast.LENGTH_SHORT).show(); return; } if (response.body() == null) { Toast.makeText(LoginActivity.this, "Verification failed", Toast.LENGTH_SHORT).show(); return; }

                ApiResponse<LoginResponseData> body = response.body();
                if (!body.status || body.data == null || body.data.token == null) {
                    Toast.makeText(LoginActivity.this, body.message != null ? body.message : "Verification failed", Toast.LENGTH_SHORT).show();
                    if (body.message != null && body.message.toLowerCase().contains("expired")) {
                        resetToLogin();
                    }
                    return;
                }

                saveTokenAndStartMain(body.data, currentUsername);
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResponseData>> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startResendCooldown(int seconds) {
        if (resendTimer != null) resendTimer.cancel();
        resendOtpButton.setEnabled(false);
        resendTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long s = Math.max(1, millisUntilFinished / 1000L);
                resendOtpButton.setText("Resend OTP (" + s + "s)");
            }

            @Override
            public void onFinish() {
                resendTimer = null;
                resendOtpButton.setEnabled(true);
                resendOtpButton.setText("Resend OTP");
            }
        };
        resendTimer.start();
    }

    private void resetToLogin() {
        if (resendTimer != null) { resendTimer.cancel(); resendTimer = null; }
        tempToken = "";
        currentUsername = "";
        currentPassword = "";
        showLoginMode();
    }

    private void showLoginMode() {
        ViewGroup root = findViewById(R.id.root);
        if (root != null) {
            TransitionManager.beginDelayedTransition(root, new AutoTransition().setDuration(220));
        }
        if (otpInput != null) otpInput.setText("");
        if (otpLayout != null) {
            otpLayout.setError(null);
            otpLayout.setVisibility(View.GONE);
        }
        if (otpHint != null) otpHint.setVisibility(View.GONE);
        if (verifyOtpButton != null) verifyOtpButton.setVisibility(View.GONE);
        if (resendOtpButton != null) {
            resendOtpButton.setVisibility(View.GONE);
            resendOtpButton.setText("Resend OTP");
        }
        if (passwordLayout != null) {
            passwordLayout.setError(null);
            passwordLayout.setVisibility(View.VISIBLE);
        }
        if (optionsRow != null) optionsRow.setVisibility(View.VISIBLE);
        if (loginButton != null) {
            loginButton.setVisibility(View.VISIBLE);
            loginButton.setEnabled(true);
        }
    }

    private void showOtpMode(String maskedEmail) {
        ViewGroup root = findViewById(R.id.root);
        if (root != null) {
            TransitionManager.beginDelayedTransition(root, new AutoTransition().setDuration(220));
        }
        if (passwordLayout != null) passwordLayout.setVisibility(View.GONE);
        if (optionsRow != null) optionsRow.setVisibility(View.GONE);
        if (loginButton != null) loginButton.setVisibility(View.GONE);
        if (otpLayout != null) {
            otpLayout.setError(null);
            otpLayout.setVisibility(View.VISIBLE);
        }
        if (verifyOtpButton != null) verifyOtpButton.setVisibility(View.VISIBLE);
        if (otpHint != null) {
            otpHint.setVisibility(View.VISIBLE);
            otpHint.setText("Enter the 6-digit code sent to " + maskedEmail + ".");
        }
        if (resendOtpButton != null) resendOtpButton.setVisibility(View.VISIBLE);
    }

    private void saveTokenAndStartMain(LoginResponseData data, String uName) {
        String realUsername = (data.admin != null && data.admin.username != null) ? data.admin.username : uName;
        String email = data.admin != null ? data.admin.email : "";
        String role = data.admin != null ? data.admin.role : "";
        tokenStore.saveSession(data.token, realUsername, email, role);
        AppLockPrefs.markUnlockedNow(this);
        AuditLog.write(this, uName, "auth.login_success", "role=" + role);

        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) { resendTimer.cancel(); resendTimer = null; }
        super.onDestroy();
    }

    private String parseError(retrofit2.Response<?> response) {
        String msg = "Request failed (" + response.code() + ")";
        try {
            if (response.errorBody() != null) {
                String errStr = response.errorBody().string();
                org.json.JSONObject jObj = new org.json.JSONObject(errStr);
                if (jObj.has("message")) {
                    msg = jObj.getString("message");
                }
            }
        } catch (Exception e) {}
        return msg;
    }

    private void restoreRememberedUsername() {
        boolean remember = loginPrefs.getBoolean(KEY_REMEMBER, false);
        rememberCheckbox.setChecked(remember);
        if (remember) {
            String savedUsername = loginPrefs.getString(KEY_USERNAME, "");
            if (savedUsername != null && !savedUsername.trim().isEmpty()) {
                usernameInput.setText(savedUsername);
                if (usernameInput.getText() != null) {
                    usernameInput.setSelection(usernameInput.getText().length());
                }
            }
        }
    }

    private void persistRememberPreference(String username) {
        SharedPreferences.Editor editor = loginPrefs.edit();
        boolean remember = rememberCheckbox.isChecked();
        editor.putBoolean(KEY_REMEMBER, remember);
        if (remember) {
            editor.putString(KEY_USERNAME, username);
        } else {
            editor.remove(KEY_USERNAME);
        }
        editor.apply();
    }
}

