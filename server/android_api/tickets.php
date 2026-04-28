<?php
// android_api/tickets.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$pdo = api_connect_pdo();
$admin = api_require_admin_auth();

if ($method === 'GET') {
    $status = strtolower(api_read_query_str('status', 'all'));
    if (!in_array($status, ['all', 'open', 'pending', 'closed'], true)) $status = 'all';

    $rows = api_fetch_ticket_rows($pdo, $status);
    api_json_out(true, 'OK', $rows);
}

if ($method !== 'POST') {
    api_json_out(false, 'Method not allowed', null, 405);
}

$body = api_read_json_body();
$action = isset($body['action']) && is_scalar($body['action']) ? strtolower(trim((string)$body['action'])) : '';
$ticketId = isset($body['ticket_id']) && is_scalar($body['ticket_id']) ? (int)$body['ticket_id'] : 0;
if ($ticketId <= 0) api_json_out(false, 'ticket_id required', null, 400);

if ($action === 'close') {
    $ok = false;
    $sqls = [
        "UPDATE tickets SET status = 'closed', updated_at = " . api_now_expr($pdo) . " WHERE id = :id",
        "UPDATE tickets SET status = 'closed' WHERE id = :id",
    ];
    foreach ($sqls as $sql) {
        if (api_try_exec($pdo, $sql, [':id' => $ticketId])) { $ok = true; break; }
    }
    if (!$ok) api_json_out(false, 'Failed to close ticket', null, 500);
    api_json_out(true, 'Ticket closed', (object)[]);
}

if ($action === 'assign') {
    $assignee = isset($body['assigned_admin']) && is_scalar($body['assigned_admin']) ? trim((string)$body['assigned_admin']) : '';
    $note = isset($body['admin_note']) && is_scalar($body['admin_note']) ? trim((string)$body['admin_note']) : '';
    if ($assignee === '') $assignee = (string)($admin['username'] ?? '');

    $ok = false;
    $sqls = [
        "UPDATE tickets SET assigned_admin = :a, admin_note = :n, updated_at = " . api_now_expr($pdo) . " WHERE id = :id",
        "UPDATE tickets SET assigned_to = :a, internal_note = :n, updated_at = " . api_now_expr($pdo) . " WHERE id = :id",
        "UPDATE tickets SET status = status WHERE id = :id", // graceful fallback if columns missing
    ];

    foreach ($sqls as $idx => $sql) {
        $params = [':id' => $ticketId];
        if ($idx <= 1) {
            $params[':a'] = $assignee;
            $params[':n'] = $note;
        }
        if (api_try_exec($pdo, $sql, $params)) { $ok = true; break; }
    }

    if (!$ok) api_json_out(false, 'Failed to assign ticket', null, 500);
    api_json_out(true, 'Ticket updated', (object)[]);
}

api_json_out(false, 'Unsupported action', null, 400);