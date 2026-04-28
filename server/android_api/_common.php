<?php
// android_api/_common.php

declare(strict_types=1);

if (is_file(__DIR__ . '/ddos_guard.php')) {
    require_once __DIR__ . '/ddos_guard.php';
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

function api_json_out(bool $status, string $message, $data = null, int $httpStatus = 200): void
{
    if (function_exists('ddos_json_out')) {
        ddos_json_out($status, $message, $data, $httpStatus);
    }

    http_response_code($httpStatus);
    echo json_encode(['status' => $status, 'message' => $message, 'data' => $data], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function api_read_json_body(): array
{
    $raw = file_get_contents('php://input');
    if (!is_string($raw) || trim($raw) === '') return [];
    $d = json_decode($raw, true);
    return is_array($d) ? $d : [];
}

function api_read_query_str(string $key, string $default = ''): string
{
    $v = $_GET[$key] ?? $default;
    if (!is_scalar($v)) return $default;
    $v = trim((string)$v);
    return $v === '' ? $default : $v;
}

function api_read_bool($v, bool $default = false): bool
{
    if ($v === null) return $default;
    if (is_bool($v)) return $v;
    if (!is_scalar($v)) return $default;
    $s = strtolower(trim((string)$v));
    if ($s === '') return $default;
    return in_array($s, ['1', 'true', 'yes', 'on', 'y'], true);
}

function api_connect_pdo(): PDO
{
    $candidates = [
        dirname(__DIR__) . '/includes/db.php',
        dirname(__DIR__, 2) . '/includes/db.php',
        dirname(__DIR__, 3) . '/includes/db.php',
        dirname(__DIR__, 4) . '/includes/db.php',
    ];

    foreach ($candidates as $path) {
        if (is_file($path)) {
            require_once $path;
            if (isset($pdo) && $pdo instanceof PDO) {
                $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
                $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
                return $pdo;
            }
        }
    }

    $dsn = getenv('DB_DSN') ?: '';
    $user = getenv('DB_USER') ?: '';
    $pass = getenv('DB_PASS') ?: '';

    if (!is_string($dsn) || trim($dsn) === '' || !is_string($user) || trim($user) === '') {
        api_json_out(false, 'Server misconfigured: missing DB connection (includes/db.php or DB_DSN/DB_USER)', null, 500);
    }

    try {
        $pdo = new PDO((string)$dsn, (string)$user, is_string($pass) ? (string)$pass : '');
        $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
        return $pdo;
    } catch (Throwable $e) {
        error_log('android_api pdo connect failed: ' . $e->getMessage());
        api_json_out(false, 'Database connection failed', null, 500);
    }
}

function api_db_driver(PDO $pdo): string
{
    try {
        return (string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME);
    } catch (Throwable $e) {
        return 'mysql';
    }
}

function api_now_expr(PDO $pdo): string
{
    return api_db_driver($pdo) === 'sqlite' ? "datetime('now')" : 'NOW()';
}

function api_admin_table_name(PDO $pdo): string
{
    foreach (['admins', 'admin_users', 'admin'] as $t) {
        try {
            $pdo->query("SELECT 1 FROM {$t} LIMIT 1");
            return $t;
        } catch (Throwable $e) {}
    }
    return 'admins';
}

function api_find_admin_by_username(PDO $pdo, string $username): ?array
{
    $table = api_admin_table_name($pdo);

    $queries = [
        "SELECT id, username, email, role, password_hash, password FROM {$table} WHERE username = :u LIMIT 1",
        "SELECT id, username, email, role, password_hash FROM {$table} WHERE username = :u LIMIT 1",
        "SELECT id, username, email, role, password FROM {$table} WHERE username = :u LIMIT 1",
        "SELECT id, username, email, role, pass AS password FROM {$table} WHERE username = :u LIMIT 1",
    ];

    foreach ($queries as $sql) {
        try {
            $st = $pdo->prepare($sql);
            $st->execute([':u' => $username]);
            $r = $st->fetch();
            if (is_array($r) && !empty($r)) return $r;
        } catch (Throwable $e) {}
    }
    return null;
}

function api_verify_password(array $admin, string $password): bool
{
    $hash = '';
    if (isset($admin['password_hash']) && is_string($admin['password_hash'])) $hash = trim($admin['password_hash']);
    if ($hash === '' && isset($admin['password']) && is_string($admin['password'])) $hash = trim($admin['password']);

    if ($hash === '') return false;

    if (str_starts_with($hash, '$2y$') || str_starts_with($hash, '$2a$') || str_starts_with($hash, '$argon2')) {
        return password_verify($password, $hash);
    }

    return hash_equals($hash, $password);
}

function api_secret(): string
{
    $s = getenv('ADMIN_API_SECRET') ?: '';
    $s = is_string($s) ? trim($s) : '';
    if ($s === '') $s = 'change-me-admin-api-secret';
    return $s;
}

function api_b64url_enc(string $raw): string
{
    return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
}

function api_b64url_dec(string $raw): string
{
    $pad = strlen($raw) % 4;
    if ($pad > 0) $raw .= str_repeat('=', 4 - $pad);
    $d = base64_decode(strtr($raw, '-_', '+/'), true);
    return is_string($d) ? $d : '';
}

function api_issue_token(array $admin): string
{
    $payload = [
        'id' => (int)($admin['id'] ?? 0),
        'username' => (string)($admin['username'] ?? ''),
        'email' => (string)($admin['email'] ?? ''),
        'role' => (string)($admin['role'] ?? 'admin'),
        'iat' => time(),
        'exp' => time() + (86400 * 14),
    ];

    $p = api_b64url_enc(json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
    $sig = hash_hmac('sha256', $p, api_secret(), true);
    return $p . '.' . api_b64url_enc($sig);
}

function api_parse_bearer_token(): string
{
    $auth = '';
    if (function_exists('getallheaders')) {
        $h = getallheaders();
        if (is_array($h)) {
            if (isset($h['Authorization']) && is_string($h['Authorization'])) $auth = $h['Authorization'];
            else if (isset($h['authorization']) && is_string($h['authorization'])) $auth = $h['authorization'];
        }
    }
    if ($auth === '') {
        $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? ($_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '');
        if (!is_string($auth)) $auth = '';
    }

    $auth = trim($auth);
    if (stripos($auth, 'bearer ') !== 0) return '';
    return trim(substr($auth, 7));
}

function api_require_admin_auth(array $allowedRoles = []): array
{
    $token = api_parse_bearer_token();
    if ($token === '') api_json_out(false, 'Unauthorized', null, 401);

    $parts = explode('.', $token, 2);
    if (count($parts) !== 2) api_json_out(false, 'Unauthorized', null, 401);

    $p = $parts[0];
    $sigGot = api_b64url_dec($parts[1]);
    $sigExp = hash_hmac('sha256', $p, api_secret(), true);
    if (!hash_equals($sigExp, $sigGot)) api_json_out(false, 'Unauthorized', null, 401);

    $json = api_b64url_dec($p);
    $payload = json_decode($json, true);
    if (!is_array($payload)) api_json_out(false, 'Unauthorized', null, 401);

    $exp = (int)($payload['exp'] ?? 0);
    if ($exp <= 0 || $exp < time()) api_json_out(false, 'Token expired', null, 401);

    $admin = [
        'id' => (int)($payload['id'] ?? 0),
        'username' => (string)($payload['username'] ?? ''),
        'email' => (string)($payload['email'] ?? ''),
        'role' => (string)($payload['role'] ?? 'admin'),
    ];

    if (!empty($allowedRoles)) {
        $r = strtolower(trim($admin['role']));
        $ok = false;
        foreach ($allowedRoles as $ar) {
            if ($r === strtolower(trim((string)$ar))) { $ok = true; break; }
        }
        if (!$ok) api_json_out(false, 'Access denied', null, 403);
    }

    return $admin;
}

function api_is_super_admin(array $admin): bool
{
    return strtolower(trim((string)($admin['role'] ?? ''))) === 'super_admin';
}

function api_settings_get(PDO $pdo): array
{
    try {
        $pdo->exec('CREATE TABLE IF NOT EXISTS admin_settings (id INTEGER PRIMARY KEY, maintenance_mode INTEGER NOT NULL DEFAULT 0, admin_email_2fa INTEGER NOT NULL DEFAULT 0)');
    } catch (Throwable $e) {
        // ignore (table may already exist with different PK syntax)
    }

    try {
        $st = $pdo->query('SELECT maintenance_mode, admin_email_2fa FROM admin_settings WHERE id = 1 LIMIT 1');
        $r = $st->fetch();
        if (is_array($r)) {
            return [
                'maintenance_mode' => ((int)($r['maintenance_mode'] ?? 0)) === 1,
                'admin_email_2fa' => ((int)($r['admin_email_2fa'] ?? 0)) === 1,
            ];
        }
    } catch (Throwable $e) {}

    // env fallback
    return [
        'maintenance_mode' => api_read_bool(getenv('MAINTENANCE_MODE') ?: '0', false),
        'admin_email_2fa' => api_read_bool(getenv('ADMIN_EMAIL_2FA') ?: '0', false),
    ];
}

function api_settings_save(PDO $pdo, bool $maintenance, bool $email2fa): array
{
    $nowExpr = api_now_expr($pdo);
    try {
        $sql = "INSERT INTO admin_settings (id, maintenance_mode, admin_email_2fa) VALUES (1, :m, :f)
                ON DUPLICATE KEY UPDATE maintenance_mode = VALUES(maintenance_mode), admin_email_2fa = VALUES(admin_email_2fa)";
        $st = $pdo->prepare($sql);
        $st->execute([':m' => $maintenance ? 1 : 0, ':f' => $email2fa ? 1 : 0]);
    } catch (Throwable $e) {
        try {
            $st = $pdo->prepare('UPDATE admin_settings SET maintenance_mode = :m, admin_email_2fa = :f WHERE id = 1');
            $st->execute([':m' => $maintenance ? 1 : 0, ':f' => $email2fa ? 1 : 0]);
            if ($st->rowCount() === 0) {
                $st2 = $pdo->prepare('INSERT INTO admin_settings (id, maintenance_mode, admin_email_2fa) VALUES (1, :m, :f)');
                $st2->execute([':m' => $maintenance ? 1 : 0, ':f' => $email2fa ? 1 : 0]);
            }
        } catch (Throwable $e2) {
            error_log('settings save failed: ' . $e2->getMessage());
            api_json_out(false, 'Failed to save settings', null, 500);
        }
    }

    return ['maintenance_mode' => $maintenance, 'admin_email_2fa' => $email2fa];
}

function api_cache_path(string $name): string
{
    return dirname(__DIR__) . '/cache/' . $name;
}

function api_otp_store_read(): array
{
    $f = api_cache_path('admin_otp_store.json');
    if (!is_file($f)) return [];
    $raw = @file_get_contents($f);
    if (!is_string($raw) || trim($raw) === '') return [];
    $d = json_decode($raw, true);
    return is_array($d) ? $d : [];
}

function api_otp_store_write(array $data): void
{
    $f = api_cache_path('admin_otp_store.json');
    @mkdir(dirname($f), 0777, true);
    @file_put_contents($f, json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
}

function api_mask_email(string $email): string
{
    $email = trim($email);
    if ($email === '' || !str_contains($email, '@')) return 'your admin email';
    [$local, $dom] = explode('@', $email, 2);
    $local = trim($local);
    if (strlen($local) <= 2) $localMasked = substr($local, 0, 1) . '*';
    else $localMasked = substr($local, 0, 2) . str_repeat('*', max(1, strlen($local) - 2));
    return $localMasked . '@' . $dom;
}

function api_send_otp_email(string $to, string $otp): void
{
    $to = trim($to);
    if ($to === '') return;

    $subject = 'Your Virtual Lab Admin OTP';
    $body = "Your OTP code is: {$otp}\n\nThis code expires in 5 minutes.";
    $headers = "From: no-reply@" . ($_SERVER['HTTP_HOST'] ?? 'localhost') . "\r\n";
    @mail($to, $subject, $body, $headers);
}

function api_fetch_ticket_rows(PDO $pdo, string $status): array
{
    $where = '';
    $params = [];
    if ($status !== '' && $status !== 'all') {
        $where = ' WHERE t.status = :s ';
        $params[':s'] = $status;
    }

    $queries = [
        "SELECT t.id, t.subject, t.status, t.created_at,
                COALESCE(t.sender_name, u.full_name, u.username, '') AS sender_name,
                COALESCE(t.sender_email, u.email, '') AS sender_email
         FROM tickets t
         LEFT JOIN users u ON u.id = t.user_id
         {$where}
         ORDER BY t.id DESC
         LIMIT 1000",

        "SELECT id, subject, status, created_at,
                COALESCE(sender_name, '') AS sender_name,
                COALESCE(sender_email, '') AS sender_email
         FROM tickets
         " . ($status !== '' && $status !== 'all' ? ' WHERE status = :s ' : '') .
         " ORDER BY id DESC LIMIT 1000",
    ];

    foreach ($queries as $sql) {
        try {
            $st = $pdo->prepare($sql);
            $st->execute($params);
            $rows = $st->fetchAll();
            if (is_array($rows)) return $rows;
        } catch (Throwable $e) {}
    }

    return [];
}

function api_try_exec(PDO $pdo, string $sql, array $params): bool
{
    try {
        $st = $pdo->prepare($sql);
        $st->execute($params);
        return true;
    } catch (Throwable $e) {
        return false;
    }
}