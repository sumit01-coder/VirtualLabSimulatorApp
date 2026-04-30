<?php
// android_api/ddos.php
// DDoS monitor API (overview + blocked list + top IPs + recent + rate history + manual block/unblock).
//
// NOTE:
// - Set env var ADMIN_API_TOKEN to require Authorization: Bearer <token>.
// - Include ddos_guard.php in ALL endpoints you want to track.

declare(strict_types=1);

require_once __DIR__ . '/ddos_guard.php';
require_once __DIR__ . '/ddos_lib.php';

function json_out(bool $status, string $message, $data = null, int $httpStatus = 200): void
{
    http_response_code($httpStatus);
    header('Content-Type: application/json; charset=utf-8');
    header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
    header("Pragma: no-cache");
    header("X-Content-Type-Options: nosniff");
    echo json_encode(['status' => $status, 'message' => $message, 'data' => $data], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function require_admin(): void
{
    $required = getenv('ADMIN_API_TOKEN') ?: '';
    if (!is_string($required) || trim($required) === '') return; // not enforced

    $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!is_string($auth)) json_out(false, 'Unauthorized', null, 401);
    $auth = trim($auth);
    if (stripos($auth, 'bearer ') !== 0) json_out(false, 'Unauthorized', null, 401);
    $token = trim(substr($auth, 7));
    if (!hash_equals(trim($required), $token)) json_out(false, 'Unauthorized', null, 401);
}

function read_action(): string
{
    $a = $_GET['action'] ?? 'overview';
    if (!is_scalar($a)) return 'overview';
    $a = strtolower(trim((string)$a));
    return $a !== '' ? $a : 'overview';
}

function read_json_body(): array
{
    $raw = file_get_contents('php://input');
    if (!is_string($raw) || trim($raw) === '') return [];
    $d = json_decode($raw, true);
    return is_array($d) ? $d : [];
}

function fmt_until(int $until): string
{
    if ($until <= 0) return 'Until: Permanent';
    return 'Until: ' . ddos_fmt_ts($until);
}

function compute_overview(array $state, int $now): array
{
    $recent = $state['recent'] ?? [];
    if (!is_array($recent)) $recent = [];

    $cut5m = $now - 300;
    $cut60 = $now - 60;
    $cut10 = $now - 10;

    $req5 = 0;
    $req60 = 0;
    $req10 = 0;
    $errs = 0;
    $ips = [];

    foreach ($recent as $r) {
        if (!is_array($r)) continue;
        $t = (int)($r['t'] ?? 0);
        if ($t < $cut5m) continue;
        $req5++;
        $ip = (string)($r['ip'] ?? '');
        if ($ip !== '') $ips[$ip] = true;
        $code = (int)($r['code'] ?? 200);
        if ($code >= 400) $errs++;
        if ($t >= $cut60) $req60++;
        if ($t >= $cut10) $req10++;
    }

    $blockedNow = 0;
    $blocked = $state['blocked'] ?? [];
    if (is_array($blocked)) {
        foreach ($blocked as $ip => $meta) {
            if (!is_array($meta)) continue;
            $until = (int)($meta['until'] ?? 0);
            if ($until <= 0 || $until > $now) $blockedNow++;
        }
    }

    return [
        'reqs_5min' => $req5,
        'reqs_60s' => $req60,
        'reqs_10s' => $req10,
        'blocked_now' => $blockedNow,
        'total_blocked' => (int)($state['total_blocked'] ?? 0),
        'unique_ips' => count($ips),
        'error_reqs' => $errs,
        'ts' => $now,
    ];
}

function compute_recent(array $state, int $now, int $limit = 50): array
{
    $recent = $state['recent'] ?? [];
    if (!is_array($recent)) $recent = [];
    $rows = [];

    $n = count($recent);
    for ($i = $n - 1; $i >= 0 && count($rows) < $limit; $i--) {
        $r = $recent[$i];
        if (!is_array($r)) continue;
        $t = (int)($r['t'] ?? 0);
        $code = (int)($r['code'] ?? 200);
        $rows[] = [
            'ip' => (string)($r['ip'] ?? ''),
            'method' => (string)($r['m'] ?? ''),
            'endpoint' => (string)($r['e'] ?? ''),
            'is_error' => $code >= 400 ? 1 : 0,
            'time_str' => gmdate('H:i:s', $t) . ' UTC',
        ];
    }
    return $rows;
}

function compute_rate_history(array $state, int $now, int $seconds = 120): array
{
    $recent = $state['recent'] ?? [];
    if (!is_array($recent)) $recent = [];

    $cut = $now - $seconds + 1;
    $buckets = [];
    for ($t = $cut; $t <= $now; $t++) $buckets[$t] = 0;

    foreach ($recent as $r) {
        if (!is_array($r)) continue;
        $t = (int)($r['t'] ?? 0);
        if ($t < $cut || $t > $now) continue;
        if (!isset($buckets[$t])) $buckets[$t] = 0;
        $buckets[$t]++;
    }

    $out = [];
    foreach ($buckets as $sec => $count) {
        $out[] = ['second_bucket' => (int)$sec, 'count' => (int)$count];
    }
    return $out;
}

function compute_blocked(array $state, int $now): array
{
    $blocked = $state['blocked'] ?? [];
    if (!is_array($blocked)) return [];
    $rows = [];
    foreach ($blocked as $ip => $meta) {
        if (!is_array($meta)) continue;
        $until = (int)($meta['until'] ?? 0);
        if ($until > 0 && $until <= $now) continue;
        $rows[] = [
            'ip' => (string)$ip,
            'blocked_at' => 'At: ' . ddos_fmt_ts((int)($meta['at'] ?? $now)),
            'blocked_until' => fmt_until($until),
            'reason' => 'Reason: ' . (string)($meta['reason'] ?? ''),
            'hit_count' => (int)($meta['hit_count'] ?? 0),
        ];
    }
    // Show newest blocks first.
    usort($rows, function ($a, $b) {
        $aa = (string)($a['blocked_at'] ?? '');
        $bb = (string)($b['blocked_at'] ?? '');
        return strcmp($bb, $aa);
    });
    return $rows;
}

function risk_label(int $req60, int $req10, int $errors60): array
{
    // Heuristic for UI chips.
    if ($req10 >= 80 || $req60 >= 300) return [2, 'DANGER'];
    if ($req10 >= 40 || $req60 >= 120 || $errors60 >= 10) return [1, 'RISK'];
    return [0, 'SAFE'];
}

function compute_top_ips(array $state, int $now, int $limit = 30): array
{
    $recent = $state['recent'] ?? [];
    if (!is_array($recent)) $recent = [];

    $cut = $now - 300;
    $per = [];
    foreach ($recent as $r) {
        if (!is_array($r)) continue;
        $t = (int)($r['t'] ?? 0);
        if ($t < $cut) continue;
        $ip = (string)($r['ip'] ?? '');
        if ($ip === '') continue;
        if (!isset($per[$ip])) {
            $per[$ip] = [
                'total' => 0,
                'errors' => 0,
                'endpoints' => [],
                'times' => [],
                'req10' => 0,
                'req60' => 0,
                'err60' => 0,
            ];
        }
        $per[$ip]['total']++;
        $code = (int)($r['code'] ?? 200);
        if ($code >= 400) $per[$ip]['errors']++;
        $ep = (string)($r['e'] ?? '');
        if ($ep !== '') $per[$ip]['endpoints'][$ep] = true;
        $per[$ip]['times'][] = $t;

        if ($t >= ($now - 10)) $per[$ip]['req10']++;
        if ($t >= ($now - 60)) {
            $per[$ip]['req60']++;
            if ($code >= 400) $per[$ip]['err60']++;
        }
    }

    $rows = [];
    foreach ($per as $ip => $m) {
        $times = $m['times'];
        sort($times);
        $avg = 0;
        if (count($times) >= 2) {
            $sum = 0;
            for ($i = 1; $i < count($times); $i++) {
                $sum += max(0, ($times[$i] - $times[$i - 1]) * 1000);
            }
            $avg = (int)round($sum / max(1, count($times) - 1));
        }
        [$cls, $label] = risk_label((int)$m['req60'], (int)$m['req10'], (int)$m['err60']);
        $rows[] = [
            'ip' => (string)$ip,
            'total' => (int)$m['total'],
            'errors' => (int)$m['errors'],
            'endpoints' => count($m['endpoints']),
            'avg_interval_ms' => $avg,
            'rf_class' => $cls,
            'rf_label' => $label,
        ];
    }

    usort($rows, function ($a, $b) {
        return ((int)($b['total'] ?? 0)) <=> ((int)($a['total'] ?? 0));
    });

    if (count($rows) > $limit) $rows = array_slice($rows, 0, $limit);
    return $rows;
}

require_admin();

$now = ddos_now();
$action = read_action();

$lock = ddos_state_lock();
$state = $lock['state'];
$fp = $lock['fp'];

ddos_recent_clean($state, $now);
ddos_blocked_clean($state, $now);

if ($action === 'block' || $action === 'unblock') {
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        ddos_state_unlock($fp);
        json_out(false, 'POST required', null, 405);
    }
    $body = read_json_body();
    $ip = isset($body['ip']) && is_scalar($body['ip']) ? trim((string)$body['ip']) : '';
    if ($ip === '') {
        ddos_state_unlock($fp);
        json_out(false, 'Missing ip', null, 400);
    }

    if ($action === 'block') {
        $dur = isset($body['duration']) && is_scalar($body['duration']) ? (int)$body['duration'] : 3600;
        if ($dur < 0) $dur = 0;
        if ($dur > 7 * 24 * 3600) $dur = 7 * 24 * 3600;
        ddos_block_ip($state, $ip, $now, $dur, 'Manual block');
        if ($fp !== null) ddos_state_save_locked($fp, $state);
        ddos_state_unlock($fp);
        json_out(true, 'Blocked', ['ip' => $ip, 'duration' => $dur]);
    } else {
        ddos_unblock_ip($state, $ip);
        if ($fp !== null) ddos_state_save_locked($fp, $state);
        ddos_state_unlock($fp);
        json_out(true, 'Unblocked', ['ip' => $ip]);
    }
}

$data = null;
if ($action === 'overview') $data = compute_overview($state, $now);
else if ($action === 'blocked') $data = compute_blocked($state, $now);
else if ($action === 'top_ips') $data = compute_top_ips($state, $now);
else if ($action === 'recent') $data = compute_recent($state, $now);
else if ($action === 'rate_history') $data = compute_rate_history($state, $now);
else {
    ddos_state_unlock($fp);
    json_out(false, 'Unknown action', ['action' => $action], 400);
}

if ($fp !== null) ddos_state_save_locked($fp, $state);
ddos_state_unlock($fp);
json_out(true, 'OK', $data);

