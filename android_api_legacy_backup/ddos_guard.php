<?php
// android_api/ddos_guard.php
// Include this at the top of endpoints to:
// - log requests into ddos_state.json
// - auto-block suspicious IPs
// - deny blocked IPs with 429

declare(strict_types=1);

require_once __DIR__ . '/ddos_lib.php';

function ddos_json_out(bool $status, string $message, $data = null, int $httpStatus = 200): void
{
    http_response_code($httpStatus);
    header('Content-Type: application/json; charset=utf-8');
    header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
    header("Pragma: no-cache");
    header("X-Content-Type-Options: nosniff");
    echo json_encode(['status' => $status, 'message' => $message, 'data' => $data], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function ddos_has_valid_admin_token(): bool
{
    $required = getenv('ADMIN_API_TOKEN') ?: '';
    if (!is_string($required) || trim($required) === '') return true; // not enforced

    $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!is_string($auth)) return false;
    $auth = trim($auth);
    if (stripos($auth, 'bearer ') !== 0) return false;
    $token = trim(substr($auth, 7));
    return hash_equals(trim($required), $token);
}

function ddos_guard_start(): void
{
    // Allow super-admin token holders to bypass IP blocking (so you don't lock yourself out).
    $bypassBlock = ddos_has_valid_admin_token();

    $now = ddos_now();
    $ip = ddos_client_ip();
    $method = ddos_method();
    $endpoint = ddos_endpoint();

    // Skip tracking for empty IPs.
    if ($ip === '' || $ip === '0.0.0.0') return;

    $lock = ddos_state_lock();
    $state = $lock['state'];
    $fp = $lock['fp'];

    ddos_recent_clean($state, $now);
    ddos_blocked_clean($state, $now);

    if (!$bypassBlock && !ddos_is_whitelisted($ip)) {
        // Hard block list
        [$blocked, $meta] = ddos_is_blocked($state, $ip, $now);
        if ($blocked) {
            ddos_inc_block_hit($state, $ip);
            ddos_add_recent($state, [
                't' => $now,
                'ip' => $ip,
                'm' => $method,
                'e' => $endpoint,
                'code' => 429,
            ]);
            if ($fp !== null) ddos_state_save_locked($fp, $state);
            ddos_state_unlock($fp);
            ddos_json_out(false, 'Blocked', ['ip' => $ip, 'reason' => (string)($meta['reason'] ?? 'blocked')], 429);
        }

        // Auto-block
        [$should, $dur, $reason] = ddos_should_auto_block($state, $ip, $now);
        if ($should) {
            ddos_block_ip($state, $ip, $now, (int)$dur, $reason);
            ddos_inc_block_hit($state, $ip);
            ddos_add_recent($state, [
                't' => $now,
                'ip' => $ip,
                'm' => $method,
                'e' => $endpoint,
                'code' => 429,
            ]);
            if ($fp !== null) ddos_state_save_locked($fp, $state);
            ddos_state_unlock($fp);
            ddos_json_out(false, 'Auto-blocked', ['ip' => $ip, 'reason' => $reason], 429);
        }
    }

    if ($fp !== null) ddos_state_save_locked($fp, $state);
    ddos_state_unlock($fp);

    // Log after the script finishes so we can capture final http_response_code().
    $GLOBALS['__ddos_guard'] = [
        't' => $now,
        'ip' => $ip,
        'm' => $method,
        'e' => $endpoint,
    ];

    register_shutdown_function(function () {
        $g = $GLOBALS['__ddos_guard'] ?? null;
        if (!is_array($g)) return;

        $now = ddos_now();
        $code = http_response_code();
        if (!is_int($code) || $code <= 0) $code = 200;

        $lock = ddos_state_lock();
        $state = $lock['state'];
        $fp = $lock['fp'];

        ddos_recent_clean($state, $now);
        ddos_blocked_clean($state, $now);
        ddos_add_recent($state, [
            't' => $now,
            'ip' => (string)($g['ip'] ?? ''),
            'm' => (string)($g['m'] ?? ''),
            'e' => (string)($g['e'] ?? ''),
            'code' => (int)$code,
        ]);

        if ($fp !== null) ddos_state_save_locked($fp, $state);
        ddos_state_unlock($fp);
    });
}

// Start immediately.
ddos_guard_start();

