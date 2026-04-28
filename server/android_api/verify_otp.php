<?php
// android_api/verify_otp.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    api_json_out(false, 'POST required', null, 405);
}

$pdo = api_connect_pdo();
$body = api_read_json_body();
$username = isset($body['username']) && is_scalar($body['username']) ? trim((string)$body['username']) : '';
$tempToken = isset($body['temp_token']) && is_scalar($body['temp_token']) ? trim((string)$body['temp_token']) : '';
$otp = isset($body['otp']) && is_scalar($body['otp']) ? trim((string)$body['otp']) : '';

if ($username === '' || $tempToken === '' || $otp === '') {
    api_json_out(false, 'username, temp_token and otp are required', null, 400);
}

$store = api_otp_store_read();
$rec = $store[$tempToken] ?? null;
if (!is_array($rec)) {
    api_json_out(false, 'OTP session expired. Please login again.', null, 400);
}

if ((string)($rec['username'] ?? '') !== $username) {
    api_json_out(false, 'OTP session mismatch', null, 400);
}

$exp = (int)($rec['expires_at'] ?? 0);
if ($exp <= 0 || $exp < time()) {
    unset($store[$tempToken]);
    api_otp_store_write($store);
    api_json_out(false, 'OTP expired. Please login again.', null, 400);
}

if (!hash_equals((string)($rec['otp'] ?? ''), $otp)) {
    api_json_out(false, 'Invalid OTP', null, 400);
}

unset($store[$tempToken]);
api_otp_store_write($store);

$admin = api_find_admin_by_username($pdo, $username);
if (!$admin) {
    api_json_out(false, 'Admin not found', null, 404);
}

$adminData = [
    'id' => (int)($admin['id'] ?? 0),
    'username' => (string)($admin['username'] ?? $username),
    'email' => (string)($admin['email'] ?? ''),
    'role' => (string)($admin['role'] ?? 'admin'),
];

$token = api_issue_token($adminData);
api_json_out(true, 'OTP verified', [
    'require_2fa' => false,
    'temp_token' => null,
    'masked_email' => null,
    'otp_expires_in' => 0,
    'resend_cooldown' => 0,
    'token' => $token,
    'admin' => $adminData,
]);