<?php
// android_api/ddos_api.php
// JSON data feed for the real-time DDoS monitor dashboard.
// Auth: valid Bearer token (admin or super_admin).

declare(strict_types=1);

// Minimal bootstrap — does NOT include _common.php to avoid circular headers.
if (is_file(__DIR__ . '/ddos_guard.php')) {
    require_once __DIR__ . '/ddos_guard.php';
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');

function ddos_api_json(array $data, int $status = 200): void
{
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

// ── Auth: Bearer token via _common helpers ────────────────────────────────
require_once __DIR__ . '/_common.php';

$admin = api_require_admin_auth();

// ── DB ────────────────────────────────────────────────────────────────────
$pdo = api_connect_pdo();

// ── Helpers ───────────────────────────────────────────────────────────────
function int_param(string $key, int $default, int $min, int $max): int
{
    $raw = $_GET[$key] ?? null;
    if ($raw === null || $raw === '') return $default;
    if (!is_scalar($raw)) return $default;
    $v = (int)$raw;
    return max($min, min($max, $v));
}

function require_post_ddos(): void
{
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        ddos_api_json(['error' => 'POST required'], 405);
    }
}

// ── Action dispatch ───────────────────────────────────────────────────────
$action  = isset($_GET['action']) ? (string)$_GET['action'] : 'overview';
$allowed = ['overview', 'blocked', 'top_ips', 'recent', 'rate_history', 'bundle', 'unblock', 'block'];
if (!in_array($action, $allowed, true)) {
    ddos_api_json(['error' => 'Unknown action'], 400);
}

// ── Data functions ────────────────────────────────────────────────────────
function ddos_fetch_overview(PDO $pdo, int $now): array
{
    try {
        $stmt = $pdo->prepare("
            SELECT
                SUM(CASE WHEN ts >= ? THEN 1 ELSE 0 END) AS reqs_5min,
                SUM(CASE WHEN ts >= ? THEN 1 ELSE 0 END) AS reqs_60s,
                SUM(CASE WHEN ts >= ? THEN 1 ELSE 0 END) AS reqs_10s,
                SUM(CASE WHEN is_error = 1 AND ts >= ? THEN 1 ELSE 0 END) AS error_reqs,
                COUNT(DISTINCT CASE WHEN ts >= ? THEN ip END) AS unique_ips
            FROM ddos_tracker
        ");
        $stmt->execute([$now - 300, $now - 60, $now - 10, $now - 300, $now - 300]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC) ?: [];
    } catch (Throwable $e) {
        $row = [];
    }

    $blocked      = 0;
    $totalBlocked = 0;
    try {
        $blocked      = (int)$pdo->query("SELECT COUNT(*) FROM ddos_blocked WHERE blocked_until > NOW()")->fetchColumn();
        $totalBlocked = (int)$pdo->query("SELECT COUNT(*) FROM ddos_blocked")->fetchColumn();
    } catch (Throwable $e) {}

    return [
        'reqs_5min'     => (int)($row['reqs_5min']   ?? 0),
        'reqs_60s'      => (int)($row['reqs_60s']    ?? 0),
        'reqs_10s'      => (int)($row['reqs_10s']    ?? 0),
        'blocked_now'   => $blocked,
        'total_blocked' => $totalBlocked,
        'unique_ips'    => (int)($row['unique_ips']  ?? 0),
        'error_reqs'    => (int)($row['error_reqs']  ?? 0),
        'ts'            => $now,
    ];
}

function ddos_fetch_blocked(PDO $pdo, int $limit): array
{
    try {
        $stmt = $pdo->query("
            SELECT ip, blocked_at, blocked_until, reason, hit_count
            FROM ddos_blocked
            WHERE blocked_until > NOW()
            ORDER BY hit_count DESC
            LIMIT {$limit}
        ");
        return $stmt ? $stmt->fetchAll(PDO::FETCH_ASSOC) : [];
    } catch (Throwable $e) { return []; }
}

function ddos_fetch_recent(PDO $pdo, int $sinceSeconds, int $limit): array
{
    try {
        $stmt = $pdo->prepare("
            SELECT ip, method, endpoint, is_error,
                   FROM_UNIXTIME(ts) AS time_str
            FROM ddos_tracker
            WHERE ts >= ?
            ORDER BY ts DESC
            LIMIT {$limit}
        ");
        $stmt->execute([time() - $sinceSeconds]);
        return $stmt->fetchAll(PDO::FETCH_ASSOC);
    } catch (Throwable $e) { return []; }
}

function ddos_fetch_rate_history(PDO $pdo, int $sinceSeconds, int $bucketSeconds): array
{
    try {
        $since = time() - $sinceSeconds;
        if ($bucketSeconds <= 1) {
            $stmt = $pdo->prepare("
                SELECT ts AS second_bucket, COUNT(*) AS count
                FROM ddos_tracker
                WHERE ts >= ?
                GROUP BY ts ORDER BY ts ASC
            ");
            $stmt->execute([$since]);
        } else {
            $stmt = $pdo->prepare("
                SELECT (FLOOR(ts / ?) * ?) AS second_bucket, COUNT(*) AS count
                FROM ddos_tracker
                WHERE ts >= ?
                GROUP BY second_bucket ORDER BY second_bucket ASC
            ");
            $stmt->execute([$bucketSeconds, $bucketSeconds, $since]);
        }
        return $stmt->fetchAll(PDO::FETCH_ASSOC);
    } catch (Throwable $e) { return []; }
}

function ddos_fetch_top_ips(PDO $pdo, int $now, int $limit): array
{
    try {
        $stmt = $pdo->prepare("
            SELECT
                ip,
                COUNT(*) AS total,
                SUM(is_error) AS errors,
                COUNT(DISTINCT endpoint) AS endpoints,
                SUM(CASE WHEN ts >= ? THEN 1 ELSE 0 END) AS req_60s,
                SUM(CASE WHEN ts >= ? THEN 1 ELSE 0 END) AS req_10s
            FROM ddos_tracker
            WHERE ts >= ?
            GROUP BY ip
            ORDER BY total DESC
            LIMIT {$limit}
        ");
        $stmt->execute([$now - 60, $now - 10, $now - 300]);
        return $stmt->fetchAll(PDO::FETCH_ASSOC);
    } catch (Throwable $e) { return []; }
}

// ── Dispatch ──────────────────────────────────────────────────────────────
try {
    $now = time();

    switch ($action) {
        case 'overview':
            ddos_api_json(ddos_fetch_overview($pdo, $now));

        case 'blocked': {
            $limit = int_param('limit', 100, 1, 300);
            ddos_api_json(ddos_fetch_blocked($pdo, $limit));
        }

        case 'top_ips': {
            $limit = int_param('limit', 30, 1, 100);
            ddos_api_json(ddos_fetch_top_ips($pdo, $now, $limit));
        }

        case 'recent': {
            $seconds = int_param('seconds', 60,  10, 600);
            $limit   = int_param('limit',   100,  1, 300);
            ddos_api_json(ddos_fetch_recent($pdo, $seconds, $limit));
        }

        case 'rate_history': {
            $seconds = int_param('seconds', 120, 30, 900);
            $bucket  = int_param('bucket',    1,  1,  10);
            ddos_api_json(ddos_fetch_rate_history($pdo, $seconds, $bucket));
        }

        case 'bundle': {
            $blockedLimit   = int_param('blocked_limit',  60,  1, 300);
            $topLimit       = int_param('top_limit',      30,  1, 100);
            $recentSeconds  = int_param('recent_seconds', 60, 10, 600);
            $recentLimit    = int_param('recent_limit',   60,  1, 200);
            $rateSeconds    = int_param('rate_seconds',  120, 30, 900);
            $rateBucket     = int_param('rate_bucket',     1,  1,  10);

            ddos_api_json([
                'ts'           => $now,
                'overview'     => ddos_fetch_overview($pdo, $now),
                'blocked'      => ddos_fetch_blocked($pdo, $blockedLimit),
                'top_ips'      => ddos_fetch_top_ips($pdo, $now, $topLimit),
                'recent'       => ddos_fetch_recent($pdo, $recentSeconds, $recentLimit),
                'rate_history' => ddos_fetch_rate_history($pdo, $rateSeconds, $rateBucket),
            ]);
        }

        case 'unblock': {
            require_post_ddos();
            if (!api_is_super_admin($admin)) ddos_api_json(['error' => 'super_admin only'], 403);
            $ip = filter_var($_POST['ip'] ?? '', FILTER_VALIDATE_IP);
            if (!$ip) ddos_api_json(['error' => 'Invalid IP'], 400);
            $st = $pdo->prepare('DELETE FROM ddos_blocked WHERE ip = ?');
            $st->execute([$ip]);
            ddos_api_json(['success' => true, 'ip' => $ip]);
        }

        case 'block': {
            require_post_ddos();
            if (!api_is_super_admin($admin)) ddos_api_json(['error' => 'super_admin only'], 403);
            $ip  = filter_var($_POST['ip'] ?? '', FILTER_VALIDATE_IP);
            $dur = min((int)($_POST['duration'] ?? 3600), 86400);
            if (!$ip) ddos_api_json(['error' => 'Invalid IP'], 400);
            if ($dur <= 0) $dur = 3600;
            $st = $pdo->prepare("
                INSERT INTO ddos_blocked (ip, blocked_at, blocked_until, reason)
                VALUES (?, NOW(), DATE_ADD(NOW(), INTERVAL ? SECOND), 'Admin-manual')
                ON DUPLICATE KEY UPDATE blocked_until = DATE_ADD(NOW(), INTERVAL ? SECOND), hit_count = hit_count + 1
            ");
            $st->execute([$ip, $dur, $dur]);
            ddos_api_json(['success' => true, 'ip' => $ip]);
        }
    }

    ddos_api_json(['error' => 'Unsupported action'], 400);

} catch (Throwable $e) {
    error_log('ddos_api error: ' . $e->getMessage());
    ddos_api_json(['error' => 'Internal error'], 500);
}
