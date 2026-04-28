<?php
// android_api/settings.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$pdo = api_connect_pdo();
$admin = api_require_admin_auth();

if (!api_is_super_admin($admin)) {
    api_json_out(false, 'Access denied (super_admin only)', null, 403);
}

if ($method === 'GET') {
    api_json_out(true, 'OK', api_settings_get($pdo));
}

if ($method !== 'POST') {
    api_json_out(false, 'Method not allowed', null, 405);
}

$body = api_read_json_body();
$maintenance = api_read_bool($body['maintenance_mode'] ?? null, false);
$email2fa = api_read_bool($body['admin_email_2fa'] ?? null, false);

$data = api_settings_save($pdo, $maintenance, $email2fa);
api_json_out(true, 'Settings updated', $data);