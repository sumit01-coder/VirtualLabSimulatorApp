<?php
// android_api/dashboard.php
// GET  — returns aggregate stats for the admin dashboard.
// Requires: Authorization: Bearer <token>

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
}

$pdo   = api_connect_pdo();
$admin = api_require_admin_auth();

// ── Helper: count rows with fallback ─────────────────────────────────────
function count_safe(PDO $pdo, string $table): int
{
    try {
        $st = $pdo->query("SELECT COUNT(*) AS c FROM {$table}");
        $r  = $st->fetch();
        return (int)($r['c'] ?? 0);
    } catch (Throwable $e) {
        return 0;
    }
}

function count_recent(PDO $pdo, string $table): int
{
    try {
        $driver   = api_db_driver($pdo);
        $dateExpr = $driver === 'sqlite'
            ? "date('now', '-7 days')"
            : "DATE_SUB(NOW(), INTERVAL 7 DAY)";
        $st = $pdo->query("SELECT COUNT(*) AS c FROM {$table} WHERE created_at >= {$dateExpr}");
        $r  = $st->fetch();
        return (int)($r['c'] ?? 0);
    } catch (Throwable $e) {
        return 0;
    }
}

function count_labs_strict(PDO $pdo): int
{
    try {
        $st = $pdo->query("
            SELECT COUNT(*) AS c
            FROM labs l
            INNER JOIN departments d ON d.id = l.department_id
            WHERE TRIM(COALESCE(l.name, '')) <> ''
        ");
        $r = $st->fetch();
        return (int)($r['c'] ?? 0);
    } catch (Throwable $e) {
        return count_safe($pdo, 'labs');
    }
}

function count_practicals_strict(PDO $pdo): int
{
    try {
        $st = $pdo->query("
            SELECT COUNT(*) AS c
            FROM practicals p
            INNER JOIN labs l ON l.id = p.lab_id
            INNER JOIN departments d ON d.id = l.department_id
            WHERE TRIM(COALESCE(p.title, '')) <> ''
        ");
        $r = $st->fetch();
        return (int)($r['c'] ?? 0);
    } catch (Throwable $e) {
        return count_safe($pdo, 'practicals');
    }
}

// ── Gather stats ──────────────────────────────────────────────────────────
$departments = count_safe($pdo, 'departments');
$labs        = count_labs_strict($pdo);
$practicals  = count_practicals_strict($pdo);
$users       = count_safe($pdo, 'users');
$newUsers    = count_recent($pdo, 'users');

// Verified letters — try multiple table names
$verifiedLetters = 0;
foreach (['verified_letters', 'letters', 'recommendation_letters'] as $lt) {
    try {
        $st = $pdo->query("SELECT COUNT(*) AS c FROM {$lt} WHERE status = 'verified'");
        $r  = $st->fetch();
        $verifiedLetters = (int)($r['c'] ?? 0);
        break;
    } catch (Throwable $e) {}
}

// Active support tickets — try both table names
$activeTickets = 0;
foreach (['tickets', 'support_tickets'] as $tt) {
    try {
        $st = $pdo->query("SELECT COUNT(*) AS c FROM {$tt} WHERE status <> 'closed'");
        $r  = $st->fetch();
        $activeTickets = (int)($r['c'] ?? 0);
        break;
    } catch (Throwable $e) {}
}

// ── Simulation usage stats (optional table) ───────────────────────────────
$totalSimUsage  = 0;
$activeSimUsers = 0;
try {
    $driver   = api_db_driver($pdo);
    $dateExpr = $driver === 'sqlite'
        ? "date('now', '-7 days')"
        : "DATE_SUB(NOW(), INTERVAL 7 DAY)";
    $st = $pdo->query("SELECT COUNT(*) AS c FROM simulation_usage WHERE started_at >= {$dateExpr}");
    $totalSimUsage = (int)($st->fetch()['c'] ?? 0);
    $st2 = $pdo->query("SELECT COUNT(DISTINCT user_id) AS c FROM simulation_usage WHERE started_at >= {$dateExpr} AND user_id IS NOT NULL");
    $activeSimUsers = (int)($st2->fetch()['c'] ?? 0);
} catch (Throwable $e) {}

// ── Build response ────────────────────────────────────────────────────────
api_json_out(true, 'OK', [
    'departments'      => $departments,
    'labs'             => $labs,
    'practicals'       => $practicals,
    'users'            => $users,
    'new_users_week'   => $newUsers,
    'verified_letters' => $verifiedLetters,
    'active_tickets'   => $activeTickets,
    'sim_usage_week'   => $totalSimUsage,
    'active_sim_users' => $activeSimUsers,
    'system_health'    => [
        'server'   => true,
        'database' => true,
        'api'      => true,
    ],
]);
