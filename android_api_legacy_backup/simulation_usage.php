<?php
require_once '../includes/db.php';

if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

header('Content-Type: application/json; charset=utf-8');

function respond(int $status, array $payload): void {
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

$raw = file_get_contents('php://input');
$data = json_decode($raw, true);
if (!is_array($data)) {
    respond(400, ['ok' => false, 'error' => 'invalid_json']);
}

$action = (string)($data['action'] ?? '');
$practical_id = filter_var($data['practical_id'] ?? null, FILTER_VALIDATE_INT);
$usage_token = (string)($data['usage_token'] ?? '');
$reason = isset($data['reason']) ? (string)$data['reason'] : null;

if (!in_array($action, ['start', 'end'], true)) {
    respond(400, ['ok' => false, 'error' => 'invalid_action']);
}
if (!$practical_id) {
    respond(400, ['ok' => false, 'error' => 'invalid_practical_id']);
}
if ($usage_token === '' || !preg_match('/^[a-zA-Z0-9\\-_.:]{8,64}$/', $usage_token)) {
    respond(400, ['ok' => false, 'error' => 'invalid_usage_token']);
}
if ($reason !== null && strlen($reason) > 32) {
    $reason = substr($reason, 0, 32);
}

// Ensure table exists (safe no-op if already created)
try {
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS simulation_usage (
            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            usage_token VARCHAR(64) NOT NULL,
            user_id INT NULL,
            session_id VARCHAR(128) NOT NULL,
            practical_id INT NOT NULL,
            started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            ended_at DATETIME NULL,
            duration_seconds INT NULL,
            ended_reason VARCHAR(32) NULL,
            ip_address VARCHAR(45) NULL,
            user_agent VARCHAR(255) NULL,
            PRIMARY KEY (id),
            UNIQUE KEY uq_usage_token (usage_token),
            KEY idx_user_practical (user_id, practical_id),
            KEY idx_practical_started (practical_id, started_at),
            KEY idx_session_started (session_id, started_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    ");
} catch (Throwable $e) {
    error_log('simulation_usage create table failed: ' . $e->getMessage());
    respond(500, ['ok' => false, 'error' => 'db_init_failed']);
}

$user_id = $_SESSION['user_id'] ?? null;
$session_id = session_id();
$ip = $_SERVER['REMOTE_ADDR'] ?? null;
$ua = (string)($_SERVER['HTTP_USER_AGENT'] ?? '');
if (strlen($ua) > 255) $ua = substr($ua, 0, 255);

try {
    if ($action === 'start') {
        $stmt = $pdo->prepare("
            INSERT IGNORE INTO simulation_usage
                (usage_token, user_id, session_id, practical_id, started_at, ip_address, user_agent)
            VALUES
                (:usage_token, :user_id, :session_id, :practical_id, NOW(), :ip, :ua)
        ");
        $stmt->execute([
            ':usage_token' => $usage_token,
            ':user_id' => $user_id,
            ':session_id' => $session_id,
            ':practical_id' => $practical_id,
            ':ip' => $ip,
            ':ua' => $ua
        ]);

        respond(200, ['ok' => true]);
    }

    // end
    $stmt = $pdo->prepare("
        UPDATE simulation_usage
        SET
            ended_at = IFNULL(ended_at, NOW()),
            ended_reason = IFNULL(ended_reason, :reason),
            duration_seconds = IFNULL(duration_seconds, TIMESTAMPDIFF(SECOND, started_at, NOW()))
        WHERE usage_token = :usage_token
          AND practical_id = :practical_id
          AND session_id = :session_id
          AND (user_id <=> :user_id)
        LIMIT 1
    ");
    $stmt->execute([
        ':reason' => $reason,
        ':usage_token' => $usage_token,
        ':practical_id' => $practical_id,
        ':session_id' => $session_id,
        ':user_id' => $user_id
    ]);

    respond(200, ['ok' => true]);
} catch (Throwable $e) {
    error_log('simulation_usage write failed: ' . $e->getMessage());
    respond(500, ['ok' => false, 'error' => 'db_write_failed']);
}

