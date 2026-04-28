<?php
// android_api/dashboard.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
}

$pdo = api_connect_pdo();
api_require_admin_auth();

function count_safe(PDO $pdo, string $table): int {
    try {
        $st = $pdo->query("SELECT COUNT(*) AS c FROM {$table}");
        $r = $st->fetch();
        return (int)($r['c'] ?? 0);
    } catch (Throwable $e) {
        return 0;
    }
}

$departments = count_safe($pdo, 'departments');
$labs = count_safe($pdo, 'labs');
$practicals = count_safe($pdo, 'practicals');
$users = count_safe($pdo, 'users');

$verifiedLetters = 0;
foreach (['letters', 'recommendation_letters'] as $lt) {
    try {
        $st = $pdo->query("SELECT COUNT(*) AS c FROM {$lt} WHERE status = 'verified'");
        $r = $st->fetch();
        $verifiedLetters = (int)($r['c'] ?? 0);
        break;
    } catch (Throwable $e) {}
}

$activeTickets = 0;
try {
    $st = $pdo->query("SELECT COUNT(*) AS c FROM tickets WHERE status <> 'closed'");
    $r = $st->fetch();
    $activeTickets = (int)($r['c'] ?? 0);
} catch (Throwable $e) {
    try {
        $activeTickets = count_safe($pdo, 'tickets');
    } catch (Throwable $e2) {}
}

api_json_out(true, 'OK', [
    'departments' => $departments,
    'labs' => $labs,
    'practicals' => $practicals,
    'users' => $users,
    'verified_letters' => $verifiedLetters,
    'active_tickets' => $activeTickets,
]);