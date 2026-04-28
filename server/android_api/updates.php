<?php
// android_api/updates.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
}

$pdo = api_connect_pdo();
api_require_admin_auth();

$latestTicket = null;
$ticketQueries = [
    "SELECT id, subject, status, created_at FROM tickets ORDER BY id DESC LIMIT 1",
    "SELECT id, title AS subject, status, created_at FROM support_tickets ORDER BY id DESC LIMIT 1",
];
foreach ($ticketQueries as $sql) {
    try {
        $st = $pdo->query($sql);
        $r = $st->fetch();
        if (is_array($r)) { $latestTicket = $r; break; }
    } catch (Throwable $e) {}
}

$latestPractical = null;
try {
    $st = $pdo->query("SELECT id, title, lab_id FROM practicals ORDER BY id DESC LIMIT 1");
    $r = $st->fetch();
    if (is_array($r)) $latestPractical = $r;
} catch (Throwable $e) {}

$settings = api_settings_get($pdo);

api_json_out(true, 'OK', [
    'latest_ticket' => $latestTicket,
    'latest_practical' => $latestPractical,
    'maintenance_mode' => (bool)($settings['maintenance_mode'] ?? false),
]);