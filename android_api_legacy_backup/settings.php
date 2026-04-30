<?php
// android_api/settings.php
require_once 'config.php';
require_once 'middleware.php';

// Verify Token (returns payload)
$admin = verify_admin_token();

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { exit(0); }

// Only super_admin can manage system settings (matches admin/settings.php)
$role = $admin['role'] ?? '';
if ($role !== 'super_admin') {
    header('HTTP/1.0 403 Forbidden');
    send_json_response(false, 'Access denied');
}

global $pdo;

function get_setting(PDO $pdo, string $key, string $default = '0'): string
{
    try {
        $stmt = $pdo->prepare("SELECT setting_value FROM settings WHERE setting_key = ? LIMIT 1");
        $stmt->execute([$key]);
        $v = $stmt->fetchColumn();
        return $v !== false ? (string)$v : $default;
    } catch (Throwable $e) {
        return $default;
    }
}

function upsert_setting(PDO $pdo, string $key, string $value): void
{
    $exists_stmt = $pdo->prepare("SELECT 1 FROM settings WHERE setting_key = ? LIMIT 1");
    $update_stmt = $pdo->prepare("UPDATE settings SET setting_value = ? WHERE setting_key = ?");
    $insert_stmt = $pdo->prepare("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?)");

    $exists_stmt->execute([$key]);
    if ($exists_stmt->fetchColumn()) {
        $update_stmt->execute([$value, $key]);
    } else {
        $insert_stmt->execute([$key, $value]);
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    $maintenance_mode = get_setting($pdo, 'maintenance_mode', '0') === '1';
    $admin_email_2fa  = get_setting($pdo, 'admin_email_2fa', '0') === '1';

    send_json_response(true, 'Settings loaded', [
        'maintenance_mode' => $maintenance_mode,
        'admin_email_2fa' => $admin_email_2fa,
    ]);
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $input = json_decode(file_get_contents('php://input'), true);
    if (!is_array($input)) $input = [];

    $maintenance_mode = !empty($input['maintenance_mode']) ? '1' : '0';
    $admin_email_2fa  = !empty($input['admin_email_2fa']) ? '1' : '0';

    try {
        upsert_setting($pdo, 'maintenance_mode', $maintenance_mode);
        upsert_setting($pdo, 'admin_email_2fa', $admin_email_2fa);
    } catch (PDOException $e) {
        header('HTTP/1.0 500 Internal Server Error');
        send_json_response(false, 'Database error: ' . $e->getMessage());
    }

    send_json_response(true, 'Settings updated', [
        'maintenance_mode' => $maintenance_mode === '1',
        'admin_email_2fa' => $admin_email_2fa === '1',
    ]);
}

send_json_response(false, 'Unsupported request method');

