<?php
// android_api/simulation_usage.php
// Public endpoint — records simulation start/end events from the web app.
// Called by JavaScript on the simulator pages (no admin auth required).
// Protected by DDoS guard and strict input validation.

declare(strict_types=1);

if (is_file(__DIR__ . '/ddos_guard.php')) {
    require_once __DIR__ . '/ddos_guard.php';
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if (($_SERVER['REQUEST_METHOD'] ?? 'POST') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

function su_respond(int $httpCode, array $payload): void
{
    http_response_code($httpCode);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? 'POST') !== 'POST') {
    su_respond(405, ['ok' => false, 'error' => 'POST required']);
}

// ── Parse input ───────────────────────────────────────────────────────────
$raw  = file_get_contents('php://input');
$data = is_string($raw) ? json_decode($raw, true) : null;
if (!is_array($data)) {
    su_respond(400, ['ok' => false, 'error' => 'invalid_json']);
}

$action       = isset($data['action'])       && is_scalar($data['action'])       ? (string)$data['action']       : '';
$practicalId  = isset($data['practical_id']) && is_numeric($data['practical_id']) ? (int)$data['practical_id']  : 0;
$usageToken   = isset($data['usage_token'])  && is_scalar($data['usage_token'])  ? (string)$data['usage_token'] : '';
$reason       = isset($data['reason'])       && is_scalar($data['reason'])       ? substr(trim((string)$data['reason']), 0, 32) : null;
$userId       = isset($data['user_id'])      && is_numeric($data['user_id'])     ? (int)$data['user_id']        : null;
$sessionId    = isset($data['session_id'])   && is_scalar($data['session_id'])   ? substr((string)$data['session_id'], 0, 128) : '';

// Validate
if (!in_array($action, ['start', 'end'], true)) {
    su_respond(400, ['ok' => false, 'error' => 'invalid_action']);
}
if ($practicalId <= 0) {
    su_respond(400, ['ok' => false, 'error' => 'invalid_practical_id']);
}
if ($usageToken === '' || !preg_match('/^[a-zA-Z0-9\-_.:]{8,64}$/', $usageToken)) {
    su_respond(400, ['ok' => false, 'error' => 'invalid_usage_token']);
}
if ($sessionId === '') {
    // Use IP + user agent as a session proxy if no session ID provided
    $sessionId = md5(($_SERVER['REMOTE_ADDR'] ?? '') . ($_SERVER['HTTP_USER_AGENT'] ?? ''));
}

// ── DB connection ─────────────────────────────────────────────────────────
$pdo = null;
$candidates = [
    dirname(__DIR__) . '/includes/db.php',
    dirname(__DIR__, 2) . '/includes/db.php',
    dirname(__DIR__, 3) . '/includes/db.php',
];
foreach ($candidates as $path) {
    if (is_file($path)) {
        require_once $path;
        if (isset($pdo) && $pdo instanceof PDO) break;
    }
}

if (!isset($pdo) || !($pdo instanceof PDO)) {
    error_log('simulation_usage: DB not available');
    su_respond(503, ['ok' => false, 'error' => 'db_unavailable']);
}

$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

// ── Create table if needed ────────────────────────────────────────────────
try {
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS simulation_usage (
            id               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
            usage_token      VARCHAR(64)      NOT NULL,
            user_id          INT              NULL,
            session_id       VARCHAR(128)     NOT NULL DEFAULT '',
            practical_id     INT              NOT NULL,
            started_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
            ended_at         DATETIME         NULL,
            duration_seconds INT              NULL,
            ended_reason     VARCHAR(32)      NULL,
            ip_address       VARCHAR(45)      NULL,
            user_agent       VARCHAR(255)     NULL,
            PRIMARY KEY (id),
            UNIQUE  KEY uq_usage_token      (usage_token),
            KEY            idx_user_practical (user_id, practical_id),
            KEY            idx_practical_started (practical_id, started_at),
            KEY            idx_session_started   (session_id, started_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
} catch (Throwable $e) {
    error_log('simulation_usage create table failed: ' . $e->getMessage());
    su_respond(500, ['ok' => false, 'error' => 'db_init_failed']);
}

$ip = $_SERVER['REMOTE_ADDR'] ?? null;
$ua = substr((string)($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 255);

// ── Handle action ─────────────────────────────────────────────────────────
try {
    if ($action === 'start') {
        $st = $pdo->prepare("
            INSERT IGNORE INTO simulation_usage
                (usage_token, user_id, session_id, practical_id, started_at, ip_address, user_agent)
            VALUES
                (:tok, :uid, :sid, :pid, NOW(), :ip, :ua)
        ");
        $st->execute([
            ':tok' => $usageToken,
            ':uid' => $userId,
            ':sid' => $sessionId,
            ':pid' => $practicalId,
            ':ip'  => $ip,
            ':ua'  => $ua,
        ]);
        su_respond(200, ['ok' => true, 'action' => 'started']);
    }

    // action === 'end'
    $st = $pdo->prepare("
        UPDATE simulation_usage
        SET
            ended_at         = IFNULL(ended_at, NOW()),
            ended_reason     = IFNULL(ended_reason, :reason),
            duration_seconds = IFNULL(duration_seconds, TIMESTAMPDIFF(SECOND, started_at, NOW()))
        WHERE usage_token   = :tok
          AND practical_id  = :pid
        LIMIT 1
    ");
    $st->execute([
        ':reason' => $reason,
        ':tok'    => $usageToken,
        ':pid'    => $practicalId,
    ]);
    su_respond(200, ['ok' => true, 'action' => 'ended', 'rows_updated' => $st->rowCount()]);

} catch (Throwable $e) {
    error_log('simulation_usage write failed: ' . $e->getMessage());
    su_respond(500, ['ok' => false, 'error' => 'db_write_failed']);
}
