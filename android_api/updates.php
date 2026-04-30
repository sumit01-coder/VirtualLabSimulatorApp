<?php
// android_api/updates.php
// GET — returns latest ticket, latest practical, maintenance mode flag.
// Requires: Authorization: Bearer <token>

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
}

$pdo = api_connect_pdo();
api_require_admin_auth();

// ── Latest ticket (try both table names) ──────────────────────────────────
$latestTicket = null;
$ticketQueries = [
    "SELECT id, subject, status, created_at FROM tickets        ORDER BY id DESC LIMIT 1",
    "SELECT id, subject, status, created_at FROM support_tickets ORDER BY id DESC LIMIT 1",
];
foreach ($ticketQueries as $sql) {
    try {
        $st = $pdo->query($sql);
        $r  = $st->fetch();
        if (is_array($r)) { $latestTicket = $r; break; }
    } catch (Throwable $e) {}
}

// ── Latest practical ──────────────────────────────────────────────────────
$latestPractical = null;
try {
    $st = $pdo->query("
        SELECT p.id, p.title, p.lab_id, l.name AS lab_name, d.name AS dept_name
        FROM practicals p
        INNER JOIN labs l        ON l.id = p.lab_id
        INNER JOIN departments d ON d.id = l.department_id
        WHERE TRIM(COALESCE(p.title, '')) <> ''
        ORDER BY p.id DESC
        LIMIT 1
    ");
    $r = $st->fetch();
    if (is_array($r)) $latestPractical = $r;
} catch (Throwable $e) {}

// ── System settings ───────────────────────────────────────────────────────
$settings = api_settings_get($pdo);

api_json_out(true, 'OK', [
    'latest_ticket'    => $latestTicket,
    'latest_practical' => $latestPractical,
    'maintenance_mode' => (bool)($settings['maintenance_mode'] ?? false),
    'admin_email_2fa'  => (bool)($settings['admin_email_2fa']  ?? false),
]);
