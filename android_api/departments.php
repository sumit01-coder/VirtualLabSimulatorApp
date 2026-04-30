<?php
// android_api/departments.php
// GET  — list departments
// POST { "action": "add|edit|delete", … }  (super_admin only for writes)
// Requires: Authorization: Bearer <token>

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$pdo    = api_connect_pdo();
$admin  = api_require_admin_auth();

// ── GET — list departments ────────────────────────────────────────────────
if ($method === 'GET') {
    try {
        $st = $pdo->query("
            SELECT id,
                   name,
                   COALESCE(description, '') AS description,
                   COALESCE(icon_class,  '') AS icon_class
            FROM departments
            WHERE TRIM(COALESCE(name, '')) <> ''
            ORDER BY name ASC
        ");
        $rows = $st->fetchAll();
        api_json_out(true, 'OK', is_array($rows) ? $rows : []);
    } catch (Throwable $e) {
        error_log('departments GET failed: ' . $e->getMessage());
        api_json_out(false, 'Failed to fetch departments', null, 500);
    }
}

// ── Writes: super_admin only ──────────────────────────────────────────────
if ($method !== 'POST') {
    api_json_out(false, 'Method not allowed', null, 405);
}

if (!api_is_super_admin($admin)) {
    api_json_out(false, 'Access denied: super_admin role required', null, 403);
}

$body   = api_read_json_body();
$action = isset($body['action']) && is_scalar($body['action']) ? strtolower(trim((string)$body['action'])) : '';
$id     = isset($body['id'])     && is_scalar($body['id'])     ? (int)$body['id']                          : 0;
$name   = isset($body['name'])   && is_scalar($body['name'])   ? trim((string)$body['name'])               : '';
$desc   = isset($body['description']) && is_scalar($body['description']) ? trim((string)$body['description']) : '';
$icon   = isset($body['icon_class'])  && is_scalar($body['icon_class'])  ? trim((string)$body['icon_class'])  : '';

// ── ADD ───────────────────────────────────────────────────────────────────
if ($action === 'add') {
    if ($name === '') api_json_out(false, 'name is required', null, 400);
    try {
        $st = $pdo->prepare('INSERT INTO departments (name, description, icon_class) VALUES (:n, :d, :i)');
        $st->execute([':n' => $name, ':d' => $desc, ':i' => $icon]);
        $newId = (int)$pdo->lastInsertId();
        api_json_out(true, 'Department added', ['id' => $newId]);
    } catch (Throwable $e) {
        error_log('departments add failed: ' . $e->getMessage());
        api_json_out(false, 'Failed to add department', null, 500);
    }
}

// ── EDIT ──────────────────────────────────────────────────────────────────
if ($action === 'edit') {
    if ($id <= 0) api_json_out(false, 'id is required', null, 400);
    if ($name === '') api_json_out(false, 'name is required', null, 400);
    try {
        $st = $pdo->prepare('UPDATE departments SET name = :n, description = :d, icon_class = :i WHERE id = :id');
        $st->execute([':id' => $id, ':n' => $name, ':d' => $desc, ':i' => $icon]);
        if ($st->rowCount() === 0) {
            api_json_out(false, 'Department not found', null, 404);
        }
        api_json_out(true, 'Department updated', (object)[]);
    } catch (Throwable $e) {
        error_log('departments edit failed: ' . $e->getMessage());
        api_json_out(false, 'Failed to update department', null, 500);
    }
}

// ── DELETE — cascade safety check ────────────────────────────────────────
if ($action === 'delete') {
    if ($id <= 0) api_json_out(false, 'id is required', null, 400);

    // Prevent deletion if labs still exist under this department
    try {
        $chk = $pdo->prepare('SELECT COUNT(*) AS c FROM labs WHERE department_id = :id');
        $chk->execute([':id' => $id]);
        $labCount = (int)($chk->fetch()['c'] ?? 0);
        if ($labCount > 0) {
            api_json_out(
                false,
                "Cannot delete: {$labCount} lab(s) still belong to this department. Remove or reassign them first.",
                ['lab_count' => $labCount],
                409
            );
        }
    } catch (Throwable $e) {
        // If labs table doesn't exist, proceed
    }

    try {
        $st = $pdo->prepare('DELETE FROM departments WHERE id = :id');
        $st->execute([':id' => $id]);
        if ($st->rowCount() === 0) {
            api_json_out(false, 'Department not found', null, 404);
        }
        api_json_out(true, 'Department deleted', (object)[]);
    } catch (Throwable $e) {
        error_log('departments delete failed: ' . $e->getMessage());
        api_json_out(false, 'Failed to delete department', null, 500);
    }
}

api_json_out(false, "Unsupported action: '{$action}'", null, 400);
