<?php
// android_api/ddos_lib.php
// Lightweight, file-backed request tracker + IP block list.
//
// Storage: ../cache/ddos_state.json (relative to android_api/)
// Designed to be included from multiple endpoints.

declare(strict_types=1);

function ddos_cache_path(string $name): string
{
    return dirname(__DIR__) . '/cache/' . $name;
}

function ddos_now(): int
{
    return time();
}

function ddos_fmt_ts(int $ts): string
{
    return gmdate('Y-m-d H:i:s', $ts) . ' UTC';
}

function ddos_client_ip(): string
{
    $ip = '';
    $xff = $_SERVER['HTTP_X_FORWARDED_FOR'] ?? '';
    if (is_string($xff) && trim($xff) !== '') {
        // XFF can be: client, proxy1, proxy2...
        $parts = explode(',', $xff);
        $first = trim((string)($parts[0] ?? ''));
        if ($first !== '') $ip = $first;
    }
    if ($ip === '') {
        $ip = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    }
    // Avoid empty.
    return $ip !== '' ? $ip : '0.0.0.0';
}

function ddos_endpoint(): string
{
    $uri = (string)($_SERVER['REQUEST_URI'] ?? '');
    if ($uri !== '') return $uri;
    $script = (string)($_SERVER['SCRIPT_NAME'] ?? '');
    return $script !== '' ? $script : 'unknown';
}

function ddos_method(): string
{
    $m = (string)($_SERVER['REQUEST_METHOD'] ?? 'GET');
    return $m !== '' ? strtoupper($m) : 'GET';
}

function ddos_whitelist(): array
{
    $raw = getenv('DDOS_WHITELIST') ?: '';
    if (!is_string($raw) || trim($raw) === '') return [];
    $ips = [];
    foreach (explode(',', $raw) as $p) {
        $p = trim($p);
        if ($p !== '') $ips[] = $p;
    }
    return $ips;
}

function ddos_is_whitelisted(string $ip): bool
{
    foreach (ddos_whitelist() as $w) {
        if ($w === $ip) return true;
    }
    return false;
}

function ddos_state_file(): string
{
    return ddos_cache_path('ddos_state.json');
}

function ddos_state_default(): array
{
    return [
        'v' => 1,
        // recent: newest last
        'recent' => [],
        // blocked: ip => meta
        'blocked' => [],
        'total_blocked' => 0,
        'config' => ddos_default_config(),
    ];
}

function ddos_default_config(): array
{
    return [
        'enabled' => true,
        'preset' => 'normal',
        'limit_10s' => 80,
        'limit_60s' => 300,
        'autoblock_seconds' => 3600,
        'updated_at' => 0,
    ];
}

function ddos_normalize_config(?array $config): array
{
    $base = ddos_default_config();
    $config = is_array($config) ? $config : [];

    $enabled = $config['enabled'] ?? $base['enabled'];
    $preset = $config['preset'] ?? $base['preset'];
    $limit10 = (int)($config['limit_10s'] ?? $base['limit_10s']);
    $limit60 = (int)($config['limit_60s'] ?? $base['limit_60s']);
    $autoblock = (int)($config['autoblock_seconds'] ?? $base['autoblock_seconds']);
    $updatedAt = (int)($config['updated_at'] ?? $base['updated_at']);

    if (!is_bool($enabled)) {
        $enabled = filter_var($enabled, FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE);
        if ($enabled === null) $enabled = true;
    }

    $preset = strtolower(trim((string)$preset));
    if (!in_array($preset, ['relaxed', 'normal', 'strict', 'custom'], true)) {
        $preset = 'normal';
    }

    $limit10 = max(5, min(5000, $limit10));
    $limit60 = max($limit10, min(20000, $limit60));
    $autoblock = max(60, min(604800, $autoblock));

    return [
        'enabled' => $enabled,
        'preset' => $preset,
        'limit_10s' => $limit10,
        'limit_60s' => $limit60,
        'autoblock_seconds' => $autoblock,
        'updated_at' => max(0, $updatedAt),
    ];
}

function ddos_preset_config(string $preset): array
{
    switch (strtolower(trim($preset))) {
        case 'relaxed':
            return ['preset' => 'relaxed', 'limit_10s' => 140, 'limit_60s' => 600, 'autoblock_seconds' => 900];
        case 'strict':
            return ['preset' => 'strict', 'limit_10s' => 35, 'limit_60s' => 120, 'autoblock_seconds' => 7200];
        default:
            return ['preset' => 'normal', 'limit_10s' => 80, 'limit_60s' => 300, 'autoblock_seconds' => 3600];
    }
}

function ddos_get_config(array $state): array
{
    return ddos_normalize_config($state['config'] ?? null);
}

function ddos_update_config(array &$state, array $input, int $now): array
{
    $current = ddos_get_config($state);
    $next = $current;

    if (array_key_exists('enabled', $input)) {
        $enabled = $input['enabled'];
        if (!is_bool($enabled)) {
            $enabled = filter_var($enabled, FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE);
        }
        if ($enabled !== null) {
            $next['enabled'] = (bool)$enabled;
        }
    }

    $presetProvided = false;
    if (isset($input['preset']) && is_scalar($input['preset'])) {
        $preset = strtolower(trim((string)$input['preset']));
        if (in_array($preset, ['relaxed', 'normal', 'strict'], true)) {
            $next = array_merge($next, ddos_preset_config($preset));
            $presetProvided = true;
        } elseif ($preset === 'custom') {
            $next['preset'] = 'custom';
            $presetProvided = true;
        }
    }

    $customTouched = false;
    if (array_key_exists('limit_10s', $input) && is_scalar($input['limit_10s'])) {
        $next['limit_10s'] = (int)$input['limit_10s'];
        $customTouched = true;
    }
    if (array_key_exists('limit_60s', $input) && is_scalar($input['limit_60s'])) {
        $next['limit_60s'] = (int)$input['limit_60s'];
        $customTouched = true;
    }
    if (array_key_exists('autoblock_seconds', $input) && is_scalar($input['autoblock_seconds'])) {
        $next['autoblock_seconds'] = (int)$input['autoblock_seconds'];
        $customTouched = true;
    }

    $next = ddos_normalize_config($next);
    if ($customTouched && !$presetProvided) {
        $next['preset'] = 'custom';
    }
    $next['updated_at'] = $now;
    $state['config'] = $next;
    return $next;
}

function ddos_state_load_locked($fp): array
{
    $raw = stream_get_contents($fp);
    if (!is_string($raw) || trim($raw) === '') return ddos_state_default();
    $decoded = json_decode($raw, true);
    if (!is_array($decoded)) return ddos_state_default();
    if (!isset($decoded['recent']) || !is_array($decoded['recent'])) $decoded['recent'] = [];
    if (!isset($decoded['blocked']) || !is_array($decoded['blocked'])) $decoded['blocked'] = [];
    if (!isset($decoded['total_blocked'])) $decoded['total_blocked'] = 0;
    $decoded['config'] = ddos_normalize_config($decoded['config'] ?? null);
    return $decoded;
}

function ddos_state_save_locked($fp, array $state): void
{
    ftruncate($fp, 0);
    rewind($fp);
    fwrite($fp, json_encode($state, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
    fflush($fp);
}

/**
 * @return array{state: array, fp: resource}
 */
function ddos_state_lock(): array
{
    $file = ddos_state_file();
    @mkdir(dirname($file), 0777, true);
    $fp = fopen($file, 'c+');
    if ($fp === false) {
        // Fail-open: tracking unavailable
        return ['state' => ddos_state_default(), 'fp' => null];
    }
    flock($fp, LOCK_EX);
    rewind($fp);
    $state = ddos_state_load_locked($fp);
    return ['state' => $state, 'fp' => $fp];
}

function ddos_state_unlock($fp): void
{
    if ($fp === null) return;
    flock($fp, LOCK_UN);
    fclose($fp);
}

function ddos_blocked_clean(array &$state, int $now): void
{
    if (!isset($state['blocked']) || !is_array($state['blocked'])) $state['blocked'] = [];
    foreach ($state['blocked'] as $ip => $meta) {
        if (!is_array($meta)) { unset($state['blocked'][$ip]); continue; }
        $until = (int)($meta['until'] ?? 0);
        if ($until > 0 && $until <= $now) {
            unset($state['blocked'][$ip]);
        }
    }
}

function ddos_recent_clean(array &$state, int $now): void
{
    if (!isset($state['recent']) || !is_array($state['recent'])) $state['recent'] = [];
    // Keep 10 minutes of history, cap size.
    $cut = $now - 600;
    $out = [];
    foreach ($state['recent'] as $r) {
        if (!is_array($r)) continue;
        $t = (int)($r['t'] ?? 0);
        if ($t >= $cut) $out[] = $r;
    }
    // Cap to prevent unbounded growth.
    $max = 2000;
    $n = count($out);
    if ($n > $max) {
        $out = array_slice($out, $n - $max, $max);
    }
    $state['recent'] = $out;
}

function ddos_is_blocked(array $state, string $ip, int $now): array
{
    if (!isset($state['blocked'][$ip]) || !is_array($state['blocked'][$ip])) return [false, null];
    $meta = $state['blocked'][$ip];
    $until = (int)($meta['until'] ?? 0);
    if ($until > 0 && $until <= $now) return [false, null];
    return [true, $meta];
}

function ddos_block_ip(array &$state, string $ip, int $now, int $durationSeconds, string $reason): void
{
    if (!isset($state['blocked']) || !is_array($state['blocked'])) $state['blocked'] = [];
    $existing = is_array($state['blocked'][$ip] ?? null) ? $state['blocked'][$ip] : [];
    $until = $durationSeconds > 0 ? ($now + $durationSeconds) : 0; // 0 = permanent
    $already = isset($state['blocked'][$ip]);
    $state['blocked'][$ip] = [
        'at' => $now,
        'until' => $until,
        'reason' => $reason,
        'hit_count' => (int)($existing['hit_count'] ?? 0),
    ];
    if (!$already) {
        $state['total_blocked'] = (int)($state['total_blocked'] ?? 0) + 1;
    }
}

function ddos_unblock_ip(array &$state, string $ip): void
{
    if (!isset($state['blocked']) || !is_array($state['blocked'])) return;
    unset($state['blocked'][$ip]);
}

function ddos_inc_block_hit(array &$state, string $ip): void
{
    if (!isset($state['blocked'][$ip]) || !is_array($state['blocked'][$ip])) return;
    $state['blocked'][$ip]['hit_count'] = (int)($state['blocked'][$ip]['hit_count'] ?? 0) + 1;
}

function ddos_add_recent(array &$state, array $row): void
{
    if (!isset($state['recent']) || !is_array($state['recent'])) $state['recent'] = [];
    $state['recent'][] = $row;
}

function ddos_count_ip_window(array $state, string $ip, int $now, int $windowSeconds): int
{
    $cut = $now - $windowSeconds;
    $n = 0;
    foreach (($state['recent'] ?? []) as $r) {
        if (!is_array($r)) continue;
        if (($r['ip'] ?? '') !== $ip) continue;
        $t = (int)($r['t'] ?? 0);
        if ($t >= $cut) $n++;
    }
    return $n;
}

function ddos_should_auto_block(array $state, string $ip, int $now): array
{
    $cfg = ddos_get_config($state);
    if (!(bool)($cfg['enabled'] ?? true)) {
        return [false, 0, ''];
    }

    // Runtime config first, env fallback second.
    $lim10 = max(5, (int)($cfg['limit_10s'] ?? (getenv('DDOS_LIMIT_10S') ?: 80)));
    $lim60 = max($lim10, (int)($cfg['limit_60s'] ?? (getenv('DDOS_LIMIT_60S') ?: 300)));
    $dur = max(60, (int)($cfg['autoblock_seconds'] ?? (getenv('DDOS_AUTOBLOCK_SECONDS') ?: 3600)));

    $c10 = ddos_count_ip_window($state, $ip, $now, 10) + 1;  // include current request
    $c60 = ddos_count_ip_window($state, $ip, $now, 60) + 1;

    if ($c10 >= $lim10) return [true, $dur, "Auto block: high rate ({$c10}/10s)"];
    if ($c60 >= $lim60) return [true, $dur, "Auto block: high rate ({$c60}/60s)"];
    return [false, 0, ""];
}
