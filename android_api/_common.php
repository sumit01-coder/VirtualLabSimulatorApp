<?php
// android_api/_common.php — shared bootstrap for every API endpoint
// ─────────────────────────────────────────────────────────────────────────────

declare(strict_types=1);

// ── DDoS guard (load first, before any output) ────────────────────────────
if (is_file(__DIR__ . '/ddos_guard.php')) {
    require_once __DIR__ . '/ddos_guard.php';
}

// ── Common response headers ───────────────────────────────────────────────
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('X-XSS-Protection: 1; mode=block');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// ── Core output ───────────────────────────────────────────────────────────

/**
 * Send a JSON response and exit.
 * Adds `meta.timestamp` and `meta.request_id` to every response.
 */
function api_json_out(bool $status, string $message, $data = null, int $httpStatus = 200): void
{
    // If DDoS layer has its own output function, honour it first.
    if (function_exists('ddos_json_out')) {
        ddos_json_out($status, $message, $data, $httpStatus);
    }

    $meta = [
        'timestamp'  => time(),
        'request_id' => bin2hex(random_bytes(8)),
    ];

    $payload = ['status' => $status, 'message' => $message, 'data' => $data, 'meta' => $meta];
    $json = json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    if (!is_string($json)) {
        $json = '{"status":false,"message":"Response encoding failed","data":null,"meta":{"timestamp":0,"request_id":"encode_error"}}';
        $httpStatus = 500;
    }

    http_response_code($httpStatus);
    echo $json;
    exit;
}

// ── Input helpers ─────────────────────────────────────────────────────────

/** Read and decode a JSON request body. Returns [] on failure. */
function api_read_json_body(): array
{
    $raw = file_get_contents('php://input');
    if (!is_string($raw) || trim($raw) === '') return [];
    $d = json_decode($raw, true);
    return is_array($d) ? $d : [];
}

/** Read a GET query-string param as a trimmed string. */
function api_read_query_str(string $key, string $default = ''): string
{
    $v = $_GET[$key] ?? $default;
    if (!is_scalar($v)) return $default;
    $v = trim((string)$v);
    return $v === '' ? $default : $v;
}

/** Read a GET query-string param as an integer. */
function api_read_int(string $key, int $default = 0, int $min = PHP_INT_MIN, int $max = PHP_INT_MAX): int
{
    $v = $_GET[$key] ?? null;
    if ($v === null) return $default;
    if (!is_scalar($v)) return $default;
    $s = trim((string)$v);
    if (!preg_match('/^-?\d+$/', $s)) return $default;
    $i = (int)$s;
    if ($i < $min) return $min;
    if ($i > $max) return $max;
    return $i;
}

/** Coerce a scalar value to bool (accepts "1","true","yes","on","y"). */
function api_read_bool($v, bool $default = false): bool
{
    if ($v === null) return $default;
    if (is_bool($v)) return $v;
    if (!is_scalar($v)) return $default;
    $s = strtolower(trim((string)$v));
    if ($s === '') return $default;
    return in_array($s, ['1', 'true', 'yes', 'on', 'y'], true);
}

// ── Database connection ───────────────────────────────────────────────────

/**
 * Connect to the database, preferring the site's shared `includes/db.php`.
 * Falls back to DB_DSN / DB_USER / DB_PASS environment variables.
 */
function api_connect_pdo(): PDO
{
    // Walk up the directory tree looking for includes/db.php
    $candidates = [
        dirname(__DIR__)      . '/includes/db.php',
        dirname(__DIR__, 2)   . '/includes/db.php',
        dirname(__DIR__, 3)   . '/includes/db.php',
        dirname(__DIR__, 4)   . '/includes/db.php',
    ];

    foreach ($candidates as $path) {
        if (is_file($path)) {
            require_once $path;
            if (isset($pdo) && $pdo instanceof PDO) {
                $pdo->setAttribute(PDO::ATTR_ERRMODE,         PDO::ERRMODE_EXCEPTION);
                $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
                $pdo->setAttribute(PDO::ATTR_EMULATE_PREPARES, false);
                return $pdo;
            }
        }
    }

    // Environment-variable fallback
    $dsn  = getenv('DB_DSN')  ?: '';
    $user = getenv('DB_USER') ?: '';
    $pass = getenv('DB_PASS') ?: '';

    if (!is_string($dsn) || trim($dsn) === '' || !is_string($user) || trim($user) === '') {
        api_json_out(false, 'Server misconfigured: missing DB connection (includes/db.php or DB_DSN/DB_USER)', null, 500);
    }

    try {
        $pdo = new PDO((string)$dsn, (string)$user, is_string($pass) ? (string)$pass : '');
        $pdo->setAttribute(PDO::ATTR_ERRMODE,            PDO::ERRMODE_EXCEPTION);
        $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
        $pdo->setAttribute(PDO::ATTR_EMULATE_PREPARES,   false);
        return $pdo;
    } catch (Throwable $e) {
        error_log('android_api pdo connect failed: ' . $e->getMessage());
        api_json_out(false, 'Database connection failed', null, 500);
    }
}

/** Return the PDO driver name (mysql, sqlite, …). */
function api_db_driver(PDO $pdo): string
{
    try {
        return (string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME);
    } catch (Throwable $e) {
        return 'mysql';
    }
}

/** Return the SQL expression for the current datetime based on driver. */
function api_now_expr(PDO $pdo): string
{
    return api_db_driver($pdo) === 'sqlite' ? "datetime('now')" : 'NOW()';
}

// ── Admin table discovery ─────────────────────────────────────────────────

/** Auto-discover the admins table name. */
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

/**
 * Find an admin by username across multiple possible column layouts.
 * Returns the row array or null if not found.
 */
function api_find_admin_by_username(PDO $pdo, string $username): ?array
{
    $table = api_admin_table_name($pdo);

    $queries = [
        "SELECT id, username, email, role, password_hash, password FROM {$table} WHERE username = :u OR email = :u LIMIT 1",
        "SELECT id, username, email, role, password_hash            FROM {$table} WHERE username = :u OR email = :u LIMIT 1",
        "SELECT id, username, email, role, password                 FROM {$table} WHERE username = :u OR email = :u LIMIT 1",
        "SELECT id, username, email, role, pass AS password         FROM {$table} WHERE username = :u OR email = :u LIMIT 1",
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

/**
 * Verify a plaintext password against an admin row.
 * Supports bcrypt ($2y$), argon2, and legacy plain-text (hash_equals).
 */
function api_verify_password(array $admin, string $password): bool
{
    $hash = '';
    if (isset($admin['password_hash']) && is_string($admin['password_hash'])) $hash = trim($admin['password_hash']);
    if ($hash === '' && isset($admin['password']) && is_string($admin['password'])) $hash = trim($admin['password']);
    if ($hash === '') return false;

    if (str_starts_with($hash, '$2y$') || str_starts_with($hash, '$2a$') || str_starts_with($hash, '$argon2')) {
        return password_verify($password, $hash);
    }

    // Legacy plain-text fallback (timing-safe)
    return hash_equals($hash, $password);
}

// ── JWT-like token (HMAC-SHA256) ──────────────────────────────────────────

/** Load legacy auth config when present so both auth stacks share one secret. */
function api_load_legacy_auth_config(): void
{
    static $loaded = false;
    if ($loaded) {
        return;
    }
    $loaded = true;

    $candidates = [
        __DIR__ . '/config.php',
        dirname(__DIR__) . '/android_api/config.php',
    ];

    foreach ($candidates as $path) {
        if (is_file($path)) {
            require_once $path;
            return;
        }
    }
}

/** Read the HMAC secret from env or legacy config, with a default sentinel. */
function api_secret(): string
{
    $candidates = [
        getenv('ADMIN_API_SECRET'),
        getenv('API_JWT_SECRET'),
    ];

    foreach ($candidates as $candidate) {
        if (is_string($candidate) && trim($candidate) !== '') {
            return trim($candidate);
        }
    }

    api_load_legacy_auth_config();

    foreach (['ADMIN_API_SECRET', 'API_JWT_SECRET', 'JWT_SECRET'] as $constName) {
        if (defined($constName)) {
            $value = constant($constName);
            if (is_string($value) && trim($value) !== '') {
                return trim($value);
            }
        }
    }

    return 'change-me-admin-api-secret';
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

/**
 * Issue a 2-part HMAC token: base64url(payload).base64url(sig)
 * Valid for 14 days.
 */
function api_issue_token(array $admin): string
{
    $payload = [
        'id'       => (int)($admin['id']       ?? 0),
        'username' => (string)($admin['username'] ?? ''),
        'email'    => (string)($admin['email']    ?? ''),
        'role'     => (string)($admin['role']     ?? 'admin'),
        'iat'      => time(),
        'exp'      => time() + (86400 * 14),
    ];

    $p   = api_b64url_enc(json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
    $sig = hash_hmac('sha256', $p, api_secret(), true);
    return $p . '.' . api_b64url_enc($sig);
}

/** Extract the raw Bearer token from the Authorization header. */
function api_parse_bearer_token(): string
{
    $auth = '';
    if (function_exists('getallheaders')) {
        $h = getallheaders();
        if (is_array($h)) {
            if (isset($h['Authorization'])  && is_string($h['Authorization']))  $auth = $h['Authorization'];
            elseif (isset($h['authorization']) && is_string($h['authorization'])) $auth = $h['authorization'];
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

function api_decode_admin_token(string $token): ?array
{
    $secret = api_secret();
    $parts = explode('.', $token);

    if (count($parts) === 2) {
        [$p, $sigPart] = $parts;
        $sigGot = api_b64url_dec($sigPart);
        $sigExp = hash_hmac('sha256', $p, $secret, true);
        if (hash_equals($sigExp, $sigGot)) {
            $json = api_b64url_dec($p);
            $payload = json_decode($json, true);
            if (is_array($payload)) return $payload;
        }
    }

    if (count($parts) === 3) {
        [$h, $p, $sigPart] = $parts;
        $sigExp = api_b64url_enc(hash_hmac('sha256', $h . '.' . $p, $secret, true));
        if (hash_equals($sigExp, $sigPart)) {
            $json = api_b64url_dec($p);
            $payload = json_decode($json, true);
            if (is_array($payload)) return $payload;
        }
    }

    return null;
}

/**
 * Require a valid HMAC Bearer token.
 * Returns the decoded admin payload array.
 * Exits with 401 on failure.
 */
function api_require_admin_auth(array $allowedRoles = []): array
{
    $token = api_parse_bearer_token();
    if ($token === '') api_json_out(false, 'Unauthorized: no token provided', null, 401);

    $payload = api_decode_admin_token($token);
    if (!is_array($payload)) api_json_out(false, 'Unauthorized: invalid token', null, 401);

    $exp = (int)($payload['exp'] ?? 0);
    if ($exp <= 0 || $exp < time()) api_json_out(false, 'Token expired — please login again', null, 401);

    $admin = [
        'id'       => (int)($payload['id'] ?? ($payload['admin_id'] ?? 0)),
        'username' => (string)($payload['username'] ?? ''),
        'email'    => (string)($payload['email'] ?? ''),
        'role'     => (string)($payload['role']     ?? 'admin'),
    ];

    if (!empty($allowedRoles)) {
        $r  = strtolower(trim($admin['role']));
        $ok = false;
        foreach ($allowedRoles as $ar) {
            if ($r === strtolower(trim((string)$ar))) { $ok = true; break; }
        }
        if (!$ok) api_json_out(false, 'Access denied: insufficient role', null, 403);
    }

    return $admin;
}

/** Return true if the admin has the super_admin role. */
function api_is_super_admin(array $admin): bool
{
    return strtolower(trim((string)($admin['role'] ?? ''))) === 'super_admin';
}

// ── Settings helpers ──────────────────────────────────────────────────────

/**
 * Read system settings.
 * Tries the dedicated `admin_settings` row first, then falls back to the
 * legacy `settings` key-value table, then environment variables.
 */
function api_settings_get(PDO $pdo): array
{
    // Try dedicated admin_settings table (single-row design)
    try {
        $pdo->exec('CREATE TABLE IF NOT EXISTS admin_settings (
            id INTEGER PRIMARY KEY,
            maintenance_mode INTEGER NOT NULL DEFAULT 0,
            admin_email_2fa  INTEGER NOT NULL DEFAULT 0
        )');
    } catch (Throwable $e) {}

    try {
        $st = $pdo->query('SELECT maintenance_mode, admin_email_2fa FROM admin_settings WHERE id = 1 LIMIT 1');
        $r  = $st->fetch();
        if (is_array($r)) {
            return [
                'maintenance_mode' => ((int)($r['maintenance_mode'] ?? 0)) === 1,
                'admin_email_2fa'  => ((int)($r['admin_email_2fa']  ?? 0)) === 1,
            ];
        }
    } catch (Throwable $e) {}

    // Fallback: legacy key-value `settings` table (used by the website)
    try {
        $getKey = function (PDO $pdo, string $key, string $default = '0'): string {
            $st = $pdo->prepare("SELECT setting_value FROM settings WHERE setting_key = :k LIMIT 1");
            $st->execute([':k' => $key]);
            $v = $st->fetchColumn();
            return ($v !== false) ? (string)$v : $default;
        };
        return [
            'maintenance_mode' => $getKey($pdo, 'maintenance_mode', '0') === '1',
            'admin_email_2fa'  => $getKey($pdo, 'admin_email_2fa',  '0') === '1',
        ];
    } catch (Throwable $e) {}

    // Final fallback: environment variables
    return [
        'maintenance_mode' => api_read_bool(getenv('MAINTENANCE_MODE') ?: '0', false),
        'admin_email_2fa'  => api_read_bool(getenv('ADMIN_EMAIL_2FA')  ?: '0', false),
    ];
}

/**
 * Persist system settings.
 * Writes to BOTH `admin_settings` (single-row) and the legacy `settings`
 * key-value table so both systems stay in sync.
 */
function api_settings_save(PDO $pdo, bool $maintenance, bool $email2fa): array
{
    $m = $maintenance ? 1 : 0;
    $f = $email2fa    ? 1 : 0;

    // ─ Write to admin_settings ─
    try {
        $sql = "INSERT INTO admin_settings (id, maintenance_mode, admin_email_2fa) VALUES (1, :m, :f)
                ON DUPLICATE KEY UPDATE maintenance_mode = VALUES(maintenance_mode), admin_email_2fa = VALUES(admin_email_2fa)";
        $pdo->prepare($sql)->execute([':m' => $m, ':f' => $f]);
    } catch (Throwable $e) {
        try {
            $st = $pdo->prepare('UPDATE admin_settings SET maintenance_mode = :m, admin_email_2fa = :f WHERE id = 1');
            $st->execute([':m' => $m, ':f' => $f]);
            if ($st->rowCount() === 0) {
                $pdo->prepare('INSERT INTO admin_settings (id, maintenance_mode, admin_email_2fa) VALUES (1, :m, :f)')
                    ->execute([':m' => $m, ':f' => $f]);
            }
        } catch (Throwable $e2) {
            error_log('admin_settings save failed: ' . $e2->getMessage());
        }
    }

    // ─ Sync to legacy key-value settings table ─
    $upsertKv = function (PDO $pdo, string $key, string $value): void {
        try {
            $exists = $pdo->prepare("SELECT 1 FROM settings WHERE setting_key = :k LIMIT 1");
            $exists->execute([':k' => $key]);
            if ($exists->fetchColumn()) {
                $pdo->prepare("UPDATE settings SET setting_value = :v WHERE setting_key = :k")
                    ->execute([':v' => $value, ':k' => $key]);
            } else {
                $pdo->prepare("INSERT INTO settings (setting_key, setting_value) VALUES (:k, :v)")
                    ->execute([':k' => $key, ':v' => $value]);
            }
        } catch (Throwable $e) {} // table might not exist — safe to ignore
    };

    $upsertKv($pdo, 'maintenance_mode', (string)$m);
    $upsertKv($pdo, 'admin_email_2fa',  (string)$f);

    return ['maintenance_mode' => $maintenance, 'admin_email_2fa' => $email2fa];
}

// ── Cache helpers ─────────────────────────────────────────────────────────

/** Return the absolute path to the cache directory file. */
function api_cache_path(string $name): string
{
    return dirname(__DIR__) . '/cache/' . $name;
}

/** Ensure the cache directory exists and is writable. */
function api_ensure_cache_dir(): void
{
    $dir = dirname(__DIR__) . '/cache';
    if (!is_dir($dir)) {
        @mkdir($dir, 0755, true);
    }
}

// ── OTP store (file-based) ────────────────────────────────────────────────

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
    api_ensure_cache_dir();
    $f = api_cache_path('admin_otp_store.json');
    $json = json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    if (!is_string($json)) {
        error_log('api_otp_store_write: json_encode failed');
        return;
    }
    @file_put_contents($f, $json, LOCK_EX);
}

/** Read an environment variable as a trimmed string. */
function api_env_str(string $key, string $default = ''): string
{
    $v = getenv($key);
    if (!is_string($v)) return $default;
    $v = trim($v);
    return $v === '' ? $default : $v;
}

// ── Email helpers ─────────────────────────────────────────────────────────

/** Mask an email address for display (e.g. su***@example.com). */
function api_mask_email(string $email): string
{
    $email = trim($email);
    if ($email === '' || !str_contains($email, '@')) return 'your admin email';
    [$local, $dom] = explode('@', $email, 2);
    $local = trim($local);
    if (strlen($local) <= 2) {
        $localMasked = substr($local, 0, 1) . '*';
    } else {
        $localMasked = substr($local, 0, 2) . str_repeat('*', max(1, strlen($local) - 2));
    }
    return $localMasked . '@' . $dom;
}

/** Send an OTP via PHPMailer. Logs on failure, never throws. */
function api_send_otp_email(string $to, string $otp): void
{
    $to = trim($to);
    if ($to === '' || !filter_var($to, FILTER_VALIDATE_EMAIL)) return;

    try {
        // Require PHPMailer
        $root_path = dirname(__DIR__); 
        $phpmailer_path = $root_path . '/PHPMailer-6.10.0/PHPMailer-6.10.0/src/';
        if (!file_exists($phpmailer_path . 'PHPMailer.php')) {
            $phpmailer_path = $root_path . '/PHPMailer-6.10.0/src/';
        }
        
        if (!file_exists($phpmailer_path . 'PHPMailer.php')) {
            error_log("api_send_otp_email: PHPMailer not found at {$phpmailer_path}");
            return;
        }

        require_once $phpmailer_path . 'Exception.php';
        require_once $phpmailer_path . 'PHPMailer.php';
        require_once $phpmailer_path . 'SMTP.php';
        
        // SMTP Config (env-first, no hardcoded credentials)
        if (!defined('SMTP_HOST')) define('SMTP_HOST', api_env_str('SMTP_HOST', 'smtp.hostinger.com'));
        if (!defined('SMTP_USER')) define('SMTP_USER', api_env_str('SMTP_USER', ''));
        if (!defined('SMTP_PASS')) define('SMTP_PASS', api_env_str('SMTP_PASS', ''));
        if (!defined('SMTP_PORT')) define('SMTP_PORT', (int)api_env_str('SMTP_PORT', '465'));
        if (!defined('SMTP_SECURE')) define('SMTP_SECURE', api_env_str('SMTP_SECURE', \PHPMailer\PHPMailer\PHPMailer::ENCRYPTION_SMTPS));
        if (SMTP_USER === '' || SMTP_PASS === '') {
            error_log('api_send_otp_email: SMTP_USER/SMTP_PASS not configured');
            return;
        }

        $mail = new \PHPMailer\PHPMailer\PHPMailer(true);
        
        $mail->isSMTP();
        $mail->Host       = SMTP_HOST; 
        $mail->SMTPAuth   = true; 
        $mail->Username   = SMTP_USER; 
        $mail->Password   = SMTP_PASS; 
        $mail->SMTPSecure = SMTP_SECURE; 
        $mail->Port       = SMTP_PORT;

        $mail->setFrom(SMTP_USER, 'Virtual Lab Security');
        $mail->addAddress($to);
        $mail->isHTML(true);
        
        $mail->Subject = 'Admin Login Verification Code';
        $mail->Body    = "<div style='font-family:sans-serif; color:#333; line-height:1.6; padding:20px; border:1px solid #ddd; max-width:600px;'>
            <h2 style='color:#0b67ff;'>Admin Login Verification</h2>
            <p>Hello,</p>
            <p>Your one-time login code is:</p>
            <div style='background:#f8f9fa; padding:15px; text-align:center; font-size:28px; font-weight:bold; letter-spacing:8px; border-radius:8px; margin:20px 0;'>
                " . htmlspecialchars($otp, ENT_QUOTES, 'UTF-8') . "
            </div>
            <p style='color:#dc3545;'>This code expires in 5 minutes.</p>
        </div>";

        $mail->send();
    } catch (\Exception $e) {
        error_log("api_send_otp_email: failed to send OTP to {$to}. Error: " . $e->getMessage());
    }
}

// ── Ticket helpers ────────────────────────────────────────────────────────

/**
 * Fetch ticket rows, trying both `tickets` and `support_tickets` table names,
 * with and without a `user_id` / `student_id` join.
 */
function api_fetch_ticket_rows(PDO $pdo, string $status): array
{
    $where  = '';
    $params = [];
    if ($status !== '' && $status !== 'all') {
        $where = ' WHERE t.status = :s ';
        $params[':s'] = $status;
    }

    $queries = [
        // tickets table with user_id → users join
        "SELECT t.id, t.subject, t.status, t.created_at,
                COALESCE(t.sender_name, u.full_name, u.username, '') AS sender_name,
                COALESCE(t.sender_email, u.email, '') AS sender_email
         FROM tickets t
         LEFT JOIN users u ON u.id = t.user_id
         {$where}
         ORDER BY t.id DESC LIMIT 1000",

        // tickets table without join
        "SELECT id, subject, status, created_at,
                COALESCE(sender_name, '') AS sender_name,
                COALESCE(sender_email, '') AS sender_email
         FROM tickets
         " . ($status !== '' && $status !== 'all' ? ' WHERE status = :s ' : '') .
         " ORDER BY id DESC LIMIT 1000",

        // support_tickets table with student_id → users join
        "SELECT t.id, t.subject, t.status, t.created_at,
                COALESCE(u.full_name, u.username, '') AS sender_name,
                COALESCE(u.email, '') AS sender_email
         FROM support_tickets t
         LEFT JOIN users u ON u.id = t.student_id
         {$where}
         ORDER BY t.id DESC LIMIT 1000",

        // support_tickets table without join
        "SELECT id, subject, status, created_at,
                '' AS sender_name, '' AS sender_email
         FROM support_tickets
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

// ── Generic helpers ───────────────────────────────────────────────────────

/**
 * Execute a prepared statement, swallowing exceptions.
 * Returns true on success, false on failure.
 */
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
