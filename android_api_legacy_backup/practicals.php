<?php
// android_api/practicals.php
// Admin API: list practicals with full content (used by Android Admin app).
//
// Supports two auth modes:
// 1) If `config.php` + `middleware.php` exist alongside this file, it uses `verify_admin_token()` (JWT) + `send_json_response()`.
// 2) Otherwise, it requires `Authorization: Bearer <ADMIN_API_TOKEN>` (configured via env).

declare(strict_types=1);

if (is_file(__DIR__ . '/ddos_guard.php')) {
    require_once __DIR__ . '/ddos_guard.php';
}

header('Content-Type: application/json; charset=utf-8');
header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
header("Pragma: no-cache");
header("X-Content-Type-Options: nosniff");

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
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

function read_int(string $key, int $default = 0): int
{
    $v = $_GET[$key] ?? $default;
    if (!is_scalar($v)) return $default;
    $v = trim((string)$v);
    if ($v === '') return $default;
    if (!preg_match('/^-?\d+$/', $v)) return $default;
    return (int)$v;
}

function base_url(): string
{
    $https = $_SERVER['HTTPS'] ?? '';
    $isHttps = is_string($https) && strtolower($https) !== 'off' && $https !== '';
    $scheme = $isHttps ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? '';
    $host = is_string($host) ? trim($host) : '';
    if ($host === '') return '';
    return $scheme . '://' . $host;
}

function split_csv_paths(?string $value): array
{
    $value = is_string($value) ? trim($value) : '';
    if ($value === '') return [];
    $parts = array_map('trim', explode(',', $value));
    $out = [];
    foreach ($parts as $p) {
        if ($p === '') continue;
        $out[] = $p;
    }
    return $out;
}

function require_env_admin_token(): void
{
    $required = getenv('ADMIN_API_TOKEN') ?: '';
    if (!is_string($required) || trim($required) === '') {
        api_json_out(false, 'Server misconfigured: ADMIN_API_TOKEN not set', null, 500);
    }

    $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!is_string($auth)) $auth = '';
    $auth = trim($auth);
    if (stripos($auth, 'bearer ') !== 0) {
        api_json_out(false, 'Unauthorized', null, 401);
    }
    $token = trim(substr($auth, 7));
    if ($token === '' || !hash_equals(trim($required), $token)) {
        api_json_out(false, 'Unauthorized', null, 401);
    }
}

function connect_pdo(): PDO
{
    // Prefer a shared db bootstrap file (as used by the website), but fall back to env DSN.
    $candidates = [
        dirname(__DIR__) . '/includes/db.php',        // <root>/server/includes/db.php (if deployed that way)
        dirname(__DIR__, 2) . '/includes/db.php',     // <root>/includes/db.php (if /android_api is directly under root)
        dirname(__DIR__, 3) . '/includes/db.php',     // <root>/includes/db.php (if /server/android_api under root)
        dirname(__DIR__, 4) . '/includes/db.php',
    ];

    foreach ($candidates as $path) {
        if (is_file($path)) {
            /** @noinspection PhpIncludeInspection */
            require_once $path;
            if (isset($pdo) && $pdo instanceof PDO) {
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
        error_log('Android API DB connection failed: ' . $e->getMessage());
        api_json_out(false, 'Database connection failed', null, 500);
    }
}

// Prefer your existing Android API structure if present (config.php + middleware.php).
$usesJwtMiddleware = is_file(__DIR__ . '/config.php') && is_file(__DIR__ . '/middleware.php');
if ($usesJwtMiddleware) {
    /** @noinspection PhpIncludeInspection */
    require_once __DIR__ . '/config.php';
    /** @noinspection PhpIncludeInspection */
    require_once __DIR__ . '/middleware.php';

    // Verify Token (JWT)
    verify_admin_token();

    /** @var PDO $pdo */
    global $pdo;
    if (!isset($pdo) || !($pdo instanceof PDO)) {
        api_json_out(false, 'Server misconfigured: $pdo not available', null, 500);
    }

    $limit = read_int('limit', 200);
    if ($limit < 1) $limit = 1;
    if ($limit > 5000) $limit = 5000;

    try {
        $stmt = $pdo->prepare("
            SELECT
                p.id,
                p.title,
                p.lab_id,
                l.name AS lab_name,
                d.name AS dept_name,
                p.overview,
                p.objective,
                p.materials_required,
                p.`procedure`,
                p.program_code,
                p.program_output,
                p.code_description,
                p.simulator_link,
                p.figure_path
            FROM practicals p
            LEFT JOIN labs l ON p.lab_id = l.id
            LEFT JOIN departments d ON l.department_id = d.id
            ORDER BY p.id DESC
            LIMIT ?
        ");
        $stmt->bindValue(1, $limit, PDO::PARAM_INT);
        $stmt->execute();
        $rows = $stmt->fetchAll();
    } catch (Throwable $e) {
        error_log('Android API practicals query failed: ' . $e->getMessage());
        // Keep message generic (don’t leak SQL details to clients)
        if (function_exists('send_json_response')) {
            header('HTTP/1.0 500 Internal Server Error');
            send_json_response(false, 'Database error');
        }
        api_json_out(false, 'Database error', null, 500);
    }
} else {
    // Standalone mode (repo-only): env token + DB bootstrap / env DSN.
    require_env_admin_token();
    $pdo = connect_pdo();

    $limit = read_int('limit', 5000);
    if ($limit < 1) $limit = 1;
    if ($limit > 5000) $limit = 5000;

    try {
        $stmt = $pdo->prepare("
            SELECT
                p.id,
                p.title,
                p.lab_id,
                l.name AS lab_name,
                d.name AS dept_name,
                p.overview,
                p.objective,
                p.materials_required,
                p.procedure,
                p.program_code,
                p.program_output,
                p.code_description,
                p.simulator_link,
                p.figure_path
            FROM practicals p
            JOIN labs l ON p.lab_id = l.id
            JOIN departments d ON l.department_id = d.id
            ORDER BY p.id DESC
            LIMIT ?
        ");
        $stmt->bindValue(1, $limit, PDO::PARAM_INT);
        $stmt->execute();
        $rows = $stmt->fetchAll();
    } catch (Throwable $e) {
        error_log('Android API practicals query failed: ' . $e->getMessage());
        api_json_out(false, 'Database query failed', null, 500);
    }
}

$base = base_url();
$uploadPrefix = $base !== '' ? ($base . '/assets/uploads/') : '';
$simPrefix = $base !== '' ? ($base . '/assets/simulators/') : '';

foreach ($rows as &$p) {
    $figs = split_csv_paths($p['figure_path'] ?? null);
    $urls = [];
    foreach ($figs as $f) {
        $urls[] = $uploadPrefix !== '' ? ($uploadPrefix . ltrim($f, '/')) : $f;
    }
    $p['figure_urls'] = $urls;

    $sim = is_string($p['simulator_link'] ?? null) ? trim((string)$p['simulator_link']) : '';
    if ($sim !== '' && $simPrefix !== '' && !preg_match('#^https?://#i', $sim)) {
        $p['simulator_url'] = $simPrefix . ltrim($sim, '/');
    } else {
        $p['simulator_url'] = $sim;
    }
}
unset($p);

if ($usesJwtMiddleware && function_exists('send_json_response')) {
    send_json_response(true, 'Practicals retrieved', $rows);
}

api_json_out(true, 'OK', $rows);
