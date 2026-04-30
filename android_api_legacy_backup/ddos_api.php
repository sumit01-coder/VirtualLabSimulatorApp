<?php
/**
 * admin/ddos_api.php
 * JSON data feed for the real-time DDoS monitor dashboard.
 *
 * - Read actions can be accessed by authenticated admins OR a time-limited shared session flag.
 * - Write actions (block/unblock) require authenticated admin + CSRF token.
 */

defined('DDOS_SHARED_ACCESS_SESSION') || define('DDOS_SHARED_ACCESS_SESSION', 'ddos_shared_access_granted');
defined('DDOS_SHARED_ACCESS_TTL') || define('DDOS_SHARED_ACCESS_TTL', 3600);

if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

$sharedAccess = !empty($_SESSION[DDOS_SHARED_ACCESS_SESSION])
    && (time() - (int)$_SESSION[DDOS_SHARED_ACCESS_SESSION]) < DDOS_SHARED_ACCESS_TTL;

function is_admin_session_valid(): bool
{
    return !empty($_SESSION['admin_loggedin']) && !empty($_SESSION['is_admin_verified']);
}

$isAdmin = is_admin_session_valid();

if (!$sharedAccess && !$isAdmin) {
    header('Content-Type: application/json; charset=utf-8');
    header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
    header("Pragma: no-cache");
    header("X-Content-Type-Options: nosniff");
    http_response_code(401);
    echo json_encode(['error' => 'Unauthorized'], JSON_UNESCAPED_SLASHES);
    exit;
}

require_once '../includes/db.php';

header('Content-Type: application/json; charset=utf-8');
header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
header("Pragma: no-cache");
header("X-Content-Type-Options: nosniff");

function json_out(array $data, int $status = 200): void
{
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function int_param(string $key, int $default, int $min, int $max): int
{
    $raw = $_GET[$key] ?? null;
    if ($raw === null || $raw === '') return $default;
    if (!is_scalar($raw)) return $default;
    $v = (int)$raw;
    if ($v < $min) return $min;
    if ($v > $max) return $max;
    return $v;
}

function require_post(): void
{
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        json_out(['error' => 'POST required'], 405);
    }
}

function require_write_access(bool $sharedAccess): void
{
    if ($sharedAccess) {
        json_out(['error' => 'Read-only access'], 403);
    }

    $csrfSession = (string)($_SESSION['ddos_csrf'] ?? '');
    $csrfPost = (string)($_POST['csrf'] ?? '');
    if ($csrfSession === '' || $csrfPost === '' || !hash_equals($csrfSession, $csrfPost)) {
        json_out(['error' => 'CSRF validation failed'], 403);
    }
}

$action = isset($_GET['action']) ? (string)$_GET['action'] : 'overview';
$allowed = ['overview', 'blocked', 'top_ips', 'recent', 'rate_history', 'bundle', 'unblock', 'block'];
if (!in_array($action, $allowed, true)) {
    json_out(['error' => 'Unknown action'], 400);
}

function fetch_overview(PDO $pdo, int $now): array
{
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

    $blocked = (int)$pdo->query("SELECT COUNT(*) FROM ddos_blocked WHERE blocked_until > NOW()")->fetchColumn();
    $totalBlocked = (int)$pdo->query("SELECT COUNT(*) FROM ddos_blocked")->fetchColumn();

    return [
        'reqs_5min' => (int)($row['reqs_5min'] ?? 0),
        'reqs_60s' => (int)($row['reqs_60s'] ?? 0),
        'reqs_10s' => (int)($row['reqs_10s'] ?? 0),
        'blocked_now' => $blocked,
        'total_blocked' => $totalBlocked,
        'unique_ips' => (int)($row['unique_ips'] ?? 0),
        'error_reqs' => (int)($row['error_reqs'] ?? 0),
        'ts' => $now,
    ];
}

function fetch_blocked(PDO $pdo, int $limit): array
{
    $limit = max(1, (int)$limit);
    $stmt = $pdo->query("
        SELECT ip, blocked_at, blocked_until, reason, hit_count
        FROM ddos_blocked
        WHERE blocked_until > NOW()
        ORDER BY hit_count DESC
        LIMIT {$limit}
    ");
    return $stmt ? $stmt->fetchAll(PDO::FETCH_ASSOC) : [];
}

function fetch_recent(PDO $pdo, int $sinceSeconds, int $limit): array
{
    $limit = max(1, (int)$limit);
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
}

function fetch_rate_history(PDO $pdo, int $sinceSeconds, int $bucketSeconds): array
{
    $since = time() - $sinceSeconds;
    if ($bucketSeconds <= 1) {
        $stmt = $pdo->prepare("
            SELECT ts AS second_bucket, COUNT(*) AS count
            FROM ddos_tracker
            WHERE ts >= ?
            GROUP BY ts
            ORDER BY ts ASC
        ");
        $stmt->execute([$since]);
        return $stmt->fetchAll(PDO::FETCH_ASSOC);
    }

    $stmt = $pdo->prepare("
        SELECT (FLOOR(ts / ?) * ?) AS second_bucket, COUNT(*) AS count
        FROM ddos_tracker
        WHERE ts >= ?
        GROUP BY second_bucket
        ORDER BY second_bucket ASC
    ");
    $stmt->execute([$bucketSeconds, $bucketSeconds, $since]);
    return $stmt->fetchAll(PDO::FETCH_ASSOC);
}

function fetch_top_ips(PDO $pdo, int $now, int $limit): array
{
    $t5m = $now - 300;
    $t60 = $now - 60;
    $t10 = $now - 10;

    $limit = max(1, (int)$limit);
    $stmt = $pdo->prepare("
        SELECT
            ip,
            COUNT(*) AS total,
            SUM(is_error) AS errors,
            COUNT(DISTINCT endpoint) AS endpoints,
            SUM(CASE WHEN ts >= ? THEN 1 ELSE 0 END) AS req_60s,
            SUM(CASE WHEN ts >= ? THEN 1 ELSE 0 END) AS req_10s,
            COUNT(DISTINCT CASE WHEN ts >= ? THEN endpoint END) AS endpoints_60s,
            SUM(CASE WHEN ts >= ? AND method='POST' THEN 1 ELSE 0 END) AS posts_60s,
            MIN(ts_ms) AS min_ts_ms,
            MAX(ts_ms) AS max_ts_ms
        FROM ddos_tracker
        WHERE ts >= ?
        GROUP BY ip
        ORDER BY total DESC
        LIMIT {$limit}
    ");
    $stmt->execute([$t60, $t10, $t60, $t60, $t5m]);
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

    require_once '../includes/ddos_shield.php';

    foreach ($rows as &$row) {
        $total = (int)($row['total'] ?? 0);
        $req60s = (int)($row['req_60s'] ?? 0);
        $req10s = (int)($row['req_10s'] ?? 0);
        $end60 = (int)($row['endpoints_60s'] ?? 0);
        $posts60 = (int)($row['posts_60s'] ?? 0);
        $errors = (int)($row['errors'] ?? 0);

        $minMs = (int)($row['min_ts_ms'] ?? 0);
        $maxMs = (int)($row['max_ts_ms'] ?? 0);
        $avgInterval = ($total > 1 && $maxMs > $minMs) ? (int)round(($maxMs - $minMs) / max(1, $total - 1)) : 9999;

        $features = [
            (float)$req60s,
            (float)$req10s,
            (float)$end60,
            (float)$avgInterval,
            0.8, // UA score unknown at aggregate level
            $req60s > 0 ? round($posts60 / $req60s, 2) : 0.0,
            $total > 0 ? round($errors / $total, 2) : 0.0,
            300.0,
        ];

        $vote = RFDDoSShield::rfPredict($features);
        $row['avg_interval_ms'] = $avgInterval;
        $row['rf_class'] = $vote;
        $row['rf_label'] = ['SAFE', 'SUSPICIOUS', 'MALICIOUS'][$vote] ?? 'UNKNOWN';

        unset($row['req_60s'], $row['req_10s'], $row['endpoints_60s'], $row['posts_60s'], $row['min_ts_ms'], $row['max_ts_ms']);
    }
    unset($row);

    return $rows;
}

try {
    switch ($action) {
        case 'overview': {
            json_out(fetch_overview($pdo, time()));
        }

        case 'blocked': {
            $limit = int_param('limit', 100, 1, 300);
            json_out(fetch_blocked($pdo, $limit));
        }

        case 'top_ips': {
            $limit = int_param('limit', 30, 1, 100);
            json_out(fetch_top_ips($pdo, time(), $limit));
        }

        case 'recent': {
            $seconds = int_param('seconds', 60, 10, 600);
            $limit = int_param('limit', 100, 1, 300);
            json_out(fetch_recent($pdo, $seconds, $limit));
        }

        case 'rate_history': {
            $seconds = int_param('seconds', 120, 30, 900);
            $bucket = int_param('bucket', 1, 1, 10);
            json_out(fetch_rate_history($pdo, $seconds, $bucket));
        }

        case 'bundle': {
            $now = time();
            $blockedLimit = int_param('blocked_limit', 60, 1, 300);
            $topLimit = int_param('top_limit', 30, 1, 100);
            $recentSeconds = int_param('recent_seconds', 60, 10, 600);
            $recentLimit = int_param('recent_limit', 60, 1, 200);
            $rateSeconds = int_param('rate_seconds', 120, 30, 900);
            $rateBucket = int_param('rate_bucket', 1, 1, 10);

            json_out([
                'ts' => $now,
                'overview' => fetch_overview($pdo, $now),
                'blocked' => fetch_blocked($pdo, $blockedLimit),
                'top_ips' => fetch_top_ips($pdo, $now, $topLimit),
                'recent' => fetch_recent($pdo, $recentSeconds, $recentLimit),
                'rate_history' => fetch_rate_history($pdo, $rateSeconds, $rateBucket),
            ]);
        }

        case 'unblock': {
            require_post();
            require_write_access($sharedAccess);

            $ip = filter_var($_POST['ip'] ?? '', FILTER_VALIDATE_IP);
            if (!$ip) json_out(['error' => 'Invalid IP'], 400);

            $stmt = $pdo->prepare("DELETE FROM ddos_blocked WHERE ip = ?");
            $stmt->execute([$ip]);
            json_out(['success' => true, 'ip' => $ip]);
        }

        case 'block': {
            require_post();
            require_write_access($sharedAccess);

            $ip = filter_var($_POST['ip'] ?? '', FILTER_VALIDATE_IP);
            $dur = min((int)($_POST['duration'] ?? 3600), 86400); // max 24h
            if (!$ip) json_out(['error' => 'Invalid IP'], 400);
            if ($dur <= 0) $dur = 3600;

            $stmt = $pdo->prepare("
                INSERT INTO ddos_blocked (ip, blocked_at, blocked_until, reason)
                VALUES (?, NOW(), DATE_ADD(NOW(), INTERVAL ? SECOND), 'Admin-manual')
                ON DUPLICATE KEY UPDATE blocked_until = DATE_ADD(NOW(), INTERVAL ? SECOND), hit_count = hit_count + 1
            ");
            $stmt->execute([$ip, $dur, $dur]);
            json_out(['success' => true, 'ip' => $ip]);
        }
    }

    json_out(['error' => 'Unsupported action'], 400);
} catch (PDOException $e) {
    json_out(['error' => 'DB error'], 500);
}
