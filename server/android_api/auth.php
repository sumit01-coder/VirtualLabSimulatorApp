<?php
// android_api/auth.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    api_json_out(false, 'POST required', null, 405);
}

$pdo = api_connect_pdo();
$body = api_read_json_body();
$username = isset($body['username']) && is_scalar($body['username']) ? trim((string)$body['username']) : '';
$password = isset($body['password']) && is_scalar($body['password']) ? (string)$body['password'] : '';

if ($username === '' || $password === '') {
    api_json_out(false, 'Username and password required', null, 400);
}

$admin = api_find_admin_by_username($pdo, $username);

$ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
$rlFile = dirname(__DIR__) . '/cache/admin_rate_limit.json';
$rlData = [];
if (is_file($rlFile)) {
    $raw = @file_get_contents($rlFile);
    if ($raw) $rlData = json_decode($raw, true) ?: [];
}
$now = time();
foreach ($rlData as $k => $v) {
    if (!isset($v['time']) || ($now - $v['time']) > 900) unset($rlData[$k]);
}
$attempts = $rlData[$ip]['attempts'] ?? 0;
if ($attempts >= 5) {
    api_json_out(false, 'Too many failed login attempts. Please try again in 15 minutes.', null, 429);
}

if (!$admin || !api_verify_password($admin, $password)) {
    $rlData[$ip] = ['attempts' => $attempts + 1, 'time' => $now];
    @file_put_contents($rlFile, json_encode($rlData, JSON_UNESCAPED_SLASHES));
    api_json_out(false, 'Invalid credentials', null, 401);
}

if (isset($rlData[$ip])) {
    unset($rlData[$ip]);
    @file_put_contents($rlFile, json_encode($rlData, JSON_UNESCAPED_SLASHES));
}

$settings = api_settings_get($pdo);
$require2fa = (bool)($settings['admin_email_2fa'] ?? false);

$adminData = [
    'id' => (int)($admin['id'] ?? 0),
    'username' => (string)($admin['username'] ?? $username),
    'email' => (string)($admin['email'] ?? ''),
    'role' => (string)($admin['role'] ?? 'admin'),
];

if ($require2fa) {
    $otp = (string)random_int(100000, 999999);
    $tempToken = bin2hex(random_bytes(16));
    $expiresAt = time() + 300;
    $cooldown = 30;

    $store = api_otp_store_read();
    $store[$tempToken] = [
        'username' => $username,
        'otp' => $otp,
        'expires_at' => $expiresAt,
        'created_at' => time(),
    ];

    foreach ($store as $k => $v) {
        $exp = (int)($v['expires_at'] ?? 0);
        if ($exp > 0 && $exp < time()) unset($store[$k]);
    }

    api_otp_store_write($store);
    api_send_otp_email((string)($adminData['email'] ?? ''), $otp);

    api_json_out(true, 'OTP sent', [
        'require_2fa' => true,
        'temp_token' => $tempToken,
        'masked_email' => api_mask_email((string)($adminData['email'] ?? '')),
        'otp_expires_in' => 300,
        'resend_cooldown' => $cooldown,
        'token' => null,
        'admin' => $adminData,
    ]);
}

$token = api_issue_token($adminData);
api_json_out(true, 'Login successful', [
    'require_2fa' => false,
    'temp_token' => null,
    'masked_email' => null,
    'otp_expires_in' => 0,
    'resend_cooldown' => 0,
    'token' => $token,
    'admin' => $adminData,
]);