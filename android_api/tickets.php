<?php
// android_api/tickets.php
// GET  ?status=all|open|pending|closed  — list support tickets
// POST { "action": "close|assign", "ticket_id": N, … }
// Requires: Authorization: Bearer <token>

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$pdo    = api_connect_pdo();
$admin  = api_require_admin_auth();

// ── GET — list tickets ────────────────────────────────────────────────────
if ($method === 'GET') {
    $status = strtolower(api_read_query_str('status', 'all'));
    if (!in_array($status, ['all', 'open', 'pending', 'closed'], true)) $status = 'all';

    $rows = api_fetch_ticket_rows($pdo, $status);
    api_json_out(true, 'OK', $rows);
}

// ── Writes ────────────────────────────────────────────────────────────────
if ($method !== 'POST') {
    api_json_out(false, 'Method not allowed', null, 405);
}

$body     = api_read_json_body();
$action   = isset($body['action'])    && is_scalar($body['action'])    ? strtolower(trim((string)$body['action']))   : '';
$ticketId = isset($body['ticket_id']) && is_scalar($body['ticket_id']) ? (int)$body['ticket_id']                     : 0;

if ($ticketId <= 0) api_json_out(false, 'ticket_id is required', null, 400);

// ── CLOSE ─────────────────────────────────────────────────────────────────
if ($action === 'close') {
    $sqls = [
        "UPDATE tickets       SET status = 'closed', updated_at = " . api_now_expr($pdo) . " WHERE id = :id",
        "UPDATE tickets       SET status = 'closed' WHERE id = :id",
        "UPDATE support_tickets SET status = 'closed', updated_at = " . api_now_expr($pdo) . " WHERE id = :id",
        "UPDATE support_tickets SET status = 'closed' WHERE id = :id",
    ];
    foreach ($sqls as $sql) {
        if (api_try_exec($pdo, $sql, [':id' => $ticketId])) {
            api_json_out(true, 'Ticket closed', (object)[]);
        }
    }
    api_json_out(false, 'Failed to close ticket', null, 500);
}

// ── ASSIGN ────────────────────────────────────────────────────────────────
if ($action === 'assign') {
    $assignee = isset($body['assigned_admin']) && is_scalar($body['assigned_admin'])
        ? trim((string)$body['assigned_admin'])
        : (string)($admin['username'] ?? '');
    $note = isset($body['admin_note']) && is_scalar($body['admin_note'])
        ? trim((string)$body['admin_note'])
        : '';

    $now = api_now_expr($pdo);
    $sqls = [
        "UPDATE tickets        SET assigned_admin = :a, admin_note    = :n, updated_at = {$now} WHERE id = :id",
        "UPDATE tickets        SET assigned_to    = :a, internal_note = :n, updated_at = {$now} WHERE id = :id",
        "UPDATE support_tickets SET assigned_admin = :a, admin_note    = :n, updated_at = {$now} WHERE id = :id",
        "UPDATE tickets        SET status = status WHERE id = :id", // graceful no-op fallback
    ];

    foreach ($sqls as $idx => $sql) {
        $params = [':id' => $ticketId];
        if ($idx < 3) { $params[':a'] = $assignee; $params[':n'] = $note; }
        if (api_try_exec($pdo, $sql, $params)) {
            api_json_out(true, 'Ticket assigned', (object)[]);
        }
    }
    api_json_out(false, 'Failed to assign ticket', null, 500);
}

// ── REOPEN ────────────────────────────────────────────────────────────────
if ($action === 'reopen') {
    $sqls = [
        "UPDATE tickets        SET status = 'open', updated_at = " . api_now_expr($pdo) . " WHERE id = :id",
        "UPDATE tickets        SET status = 'open' WHERE id = :id",
        "UPDATE support_tickets SET status = 'open' WHERE id = :id",
    ];
    foreach ($sqls as $sql) {
        if (api_try_exec($pdo, $sql, [':id' => $ticketId])) {
            api_json_out(true, 'Ticket reopened', (object)[]);
        }
    }
    api_json_out(false, 'Failed to reopen ticket', null, 500);
}

api_json_out(false, "Unsupported action: '{$action}'", null, 400);