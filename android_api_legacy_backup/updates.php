<?php
// android_api/updates.php
require_once 'config.php';
require_once 'middleware.php';

// Verify Token
$admin = verify_admin_token();

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { exit(0); }

global $pdo;

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('HTTP/1.0 405 Method Not Allowed');
    send_json_response(false, 'GET required');
}

try {
    $ticketStmt = $pdo->query("SELECT id, subject, status, created_at FROM support_tickets ORDER BY id DESC LIMIT 1");
    $latestTicket = $ticketStmt->fetch(PDO::FETCH_ASSOC) ?: null;

    $practicalStmt = $pdo->query("SELECT id, title, lab_id FROM practicals ORDER BY id DESC LIMIT 1");
    $latestPractical = $practicalStmt->fetch(PDO::FETCH_ASSOC) ?: null;

    $maintenanceStmt = $pdo->prepare("SELECT setting_value FROM settings WHERE setting_key = 'maintenance_mode' LIMIT 1");
    $maintenanceStmt->execute();
    $maintenanceVal = $maintenanceStmt->fetchColumn();
    $maintenanceMode = ($maintenanceVal === '1');

    send_json_response(true, 'Updates loaded', [
        'latest_ticket' => $latestTicket,
        'latest_practical' => $latestPractical,
        'maintenance_mode' => $maintenanceMode
    ]);
} catch (PDOException $e) {
    header('HTTP/1.0 500 Internal Server Error');
    send_json_response(false, 'Database error: ' . $e->getMessage());
}
