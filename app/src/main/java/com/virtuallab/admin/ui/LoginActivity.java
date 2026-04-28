package com.virtuallab.admin.ui;

import android.content.Intent;
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
    private TokenStore tokenStore;
    private ApiService api;

    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputEditText otpInput;
    private Button loginButton;
    private Button verifyOtpButton;
    private MaterialButton resendOtpButton;
    private TextView otpHint;
    private ProgressBar progress;
    
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
        otpHint = findViewById(R.id.otpHint);
        progress = findViewById(R.id.progress);
        passwordLayout = findViewById(R.id.passwordLayout);
        otpLayout = findViewById(R.id.otpLayout);

        loginButton.setOnClickListener(v -> doLogin());
        verifyOtpButton.setOnClickListener(v -> doVerifyOtp());
        resendOtpButton.setOnClickListener(v -> doResendOtp());
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        verifyOtpButton.setEnabled(!loading);
        resendOtpButton.setEnabled(!loading && (resendTimer == null));
    }

    private void doLogin() {
        String username = usernameInput.getText() != null ? usernameInput.getText().toString().trim() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        api.login(new LoginRequest(username, password)).enqueue(new Callback<ApiResponse<LoginResponseData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponseData>> call, Response<ApiResponse<LoginResponseData>> response) {
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(LoginActivity.this, "Login failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    return;
                }

                ApiResponse<LoginResponseData> body = response.body();
                if (!body.status || body.data == null) {
                    Toast.makeText(LoginActivity.this, body.message != null ? body.message : "Login failed", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (body.data.require_2fa) {
                     // Show OTP view
                     tempToken = body.data.temp_token;
                     currentUsername = username;
                     currentPassword = password;

                     ViewGroup root = findViewById(R.id.root);
                     TransitionManager.beginDelayedTransition(root, new AutoTransition().setDuration(220));
                     passwordLayout.setVisibility(View.GONE);
                     loginButton.setVisibility(View.GONE);
                     
                     otpLayout.setVisibility(View.VISIBLE);
                     verifyOtpButton.setVisibility(View.VISIBLE);
                     otpHint.setVisibility(View.VISIBLE);
                     resendOtpButton.setVisibility(View.VISIBLE);

                     String masked = body.data.masked_email != null && !body.data.masked_email.trim().isEmpty()
                             ? body.data.masked_email.trim()
                             : "your admin email";
                     otpHint.setText("Enter the 6-digit code sent to " + masked + ".");

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
                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Toast.makeText(LoginActivity.this, "Resend failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    return;
                }

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
            Toast.makeText(this, "Enter 6-digit OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        api.verifyOtp(new VerifyOtpRequest(currentUsername, tempToken, otp)).enqueue(new Callback<ApiResponse<LoginResponseData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponseData>> call, Response<ApiResponse<LoginResponseData>> response) {
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(LoginActivity.this, "Verification failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    return;
                }

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

        ViewGroup root = findViewById(R.id.root);
        TransitionManager.beginDelayedTransition(root, new AutoTransition().setDuration(220));
        otpInput.setText("");
        otpLayout.setVisibility(View.GONE);
        verifyOtpButton.setVisibility(View.GONE);
        resendOtpButton.setVisibility(View.GONE);
        otpHint.setVisibility(View.GONE);

        passwordLayout.setVisibility(View.VISIBLE);
        loginButton.setVisibility(View.VISIBLE);
        loginButton.setEnabled(true);
    }

    private void saveTokenAndStartMain(LoginResponseData data, String uName) {
        String email = data.admin != null ? data.admin.email : "";
        String role = data.admin != null ? data.admin.role : "";
        tokenStore.saveSession(data.token, uName, email, role);
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
}
