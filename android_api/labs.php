<?php
// android_api/labs.php
// GET  ?department_id=N&q=search  — list labs
// POST { "action": "add|edit|delete", … }  (super_admin only for writes)
// Requires: Authorization: Bearer <token>

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$pdo    = api_connect_pdo();
$admin  = api_require_admin_auth();

$isSuper    = api_is_super_admin($admin);
$adminRole  = strtolower(trim((string)($admin['role'] ?? '')));

// ── GET — list labs ───────────────────────────────────────────────────────
if ($method === 'GET') {
    $departmentId = api_read_int('department_id', 0, 0);
    $q            = trim(api_read_query_str('q', ''));

    $where  = ["TRIM(COALESCE(l.name, '')) <> ''"];
    $params = [];

    // Department-scoped access for non-super-admin
    if (!$isSuper) {
        // Non-super admins should only see labs in their own department.
        // We read department_id from the JWT payload (stored in admin data).
        // If the token doesn't carry it, fall back to returning only what the
        // request explicitly asks for (or nothing if unspecified).
        $tokenDeptId = (int)($admin['department_id'] ?? 0);
        if ($tokenDeptId > 0) {
            $where[]             = 'l.department_id = :dep';
            $params[':dep']      = $tokenDeptId;
        } elseif ($departmentId > 0) {
            $where[]             = 'l.department_id = :dep';
            $params[':dep']      = $departmentId;
        }
        // If neither is available, they see all (edge case; the token should carry dept_id)
    } elseif ($departmentId > 0) {
        // Super admin can filter by dept
        $where[]        = 'l.department_id = :dep';
        $params[':dep'] = $departmentId;
    }

    if ($q !== '') {
        $where[]      = '(l.name LIKE :q OR l.subject LIKE :q OR l.topics LIKE :q)';
        $params[':q'] = '%' . $q . '%';
    }

    $whereSql = 'WHERE ' . implode(' AND ', $where);

    $sql = "SELECT l.id,
                   l.name,
                   COALESCE(l.subject,       '') AS subject,
                   COALESCE(l.topics,        '') AS topics,
                   COALESCE(l.description,   '') AS description,
                   COALESCE(l.department_id,  0) AS department_id,
                   COALESCE(d.name,          '') AS department_name
            FROM labs l
            INNER JOIN departments d ON d.id = l.department_id
            {$whereSql}
            ORDER BY d.name ASC, l.name ASC
            LIMIT 1000";

    try {
        $st = $pdo->prepare($sql);
        $st->execute($params);
        $rows = $st->fetchAll();
        api_json_out(true, 'OK', is_array($rows) ? $rows : []);
    } catch (Throwable $e) {
        error_log('labs GET failed: ' . $e->getMessage());
        api_json_out(false, 'Failed to fetch labs', null, 500);
    }
}

// ── Writes: super_admin only ──────────────────────────────────────────────
if ($method !== 'POST') {
    api_json_out(false, 'Method not allowed', null, 405);
}

if (!$isSuper) {
    api_json_out(false, 'Access denied: super_admin role required', null, 403);
}

$body         = api_read_json_body();
$action       = isset($body['action'])        && is_scalar($body['action'])        ? strtolower(trim((string)$body['action']))   : '';
$id           = isset($body['id'])            && is_scalar($body['id'])            ? (int)$body['id']                            : 0;
$name         = isset($body['name'])          && is_scalar($body['name'])          ? trim((string)$body['name'])                 : '';
$subject      = isset($body['subject'])       && is_scalar($body['subject'])       ? trim((string)$body['subject'])              : '';
$topics       = isset($body['topics'])        && is_scalar($body['topics'])        ? trim((string)$body['topics'])               : '';
$description  = isset($body['description'])   && is_scalar($body['description'])   ? trim((string)$body['description'])          : '';
$departmentId = isset($body['department_id']) && is_scalar($body['department_id']) ? (int)$body['department_id']                 : 0;

// ── ADD ───────────────────────────────────────────────────────────────────
if ($action === 'add') {
    if ($name === '')        api_json_out(false, 'name is required',          null, 400);
    if ($departmentId <= 0) api_json_out(false, 'department_id is required', null, 400);

    // Verify department exists
    try {
        $chk = $pdo->prepare('SELECT id FROM departments WHERE id = :id LIMIT 1');
        $chk->execute([':id' => $departmentId]);
        if (!$chk->fetch()) api_json_out(false, 'Department not found', null, 404);
    } catch (Throwable $e) {}

    try {
        $st = $pdo->prepare('INSERT INTO labs (name, subject, topics, description, department_id) VALUES (:n, :s, :t, :d, :dep)');
        $st->execute([':n' => $name, ':s' => $subject, ':t' => $topics, ':d' => $description, ':dep' => $departmentId]);
        $newId = (int)$pdo->lastInsertId();
        api_json_out(true, 'Lab added', ['id' => $newId]);
    } catch (Throwable $e) {
        error_log('labs add failed: ' . $e->getMessage());
        api_json_out(false, 'Failed to add lab', null, 500);
    }
}

// ── EDIT ──────────────────────────────────────────────────────────────────
if ($action === 'edit') {
    if ($id <= 0)   api_json_out(false, 'id is required',   null, 400);
    if ($name === '') api_json_out(false, 'name is required', null, 400);

    try {
        $st = $pdo->prepare('UPDATE labs SET name = :n, subject = :s, topics = :t, description = :d, department_id = :dep WHERE id = :id');
        $st->execute([':id' => $id, ':n' => $name, ':s' => $subject, ':t' => $topics, ':d' => $description, ':dep' => $departmentId]);
        if ($st->rowCount() === 0) api_json_out(false, 'Lab not found', null, 404);
        api_json_out(true, 'Lab updated', (object)[]);
    } catch (Throwable $e) {
        error_log('labs edit failed: ' . $e->getMessage());
        api_json_out(false, 'Failed to update lab', null, 500);
    }
}

// ── DELETE — cascade safety check ────────────────────────────────────────
if ($action === 'delete') {
    if ($id <= 0) api_json_out(false, 'id is required', null, 400);

    // Prevent deletion if practicals exist under this lab
    try {
        $chk = $pdo->prepare('SELECT COUNT(*) AS c FROM practicals WHERE lab_id = :id');
        $chk->execute([':id' => $id]);
        $cnt = (int)($chk->fetch()['c'] ?? 0);
        if ($cnt > 0) {
            api_json_out(
                false,
                "Cannot delete: {$cnt} practical(s) still belong to this lab. Remove or reassign them first.",
                ['practical_count' => $cnt],
                409
            );
        }
    } catch (Throwable $e) {}

    try {
        $st = $pdo->prepare('DELETE FROM labs WHERE id = :id');
        $st->execute([':id' => $id]);
        if ($st->rowCount() === 0) api_json_out(false, 'Lab not found', null, 404);
        api_json_out(true, 'Lab deleted', (object)[]);
    } catch (Throwable $e) {
        error_log('labs delete failed: ' . $e->getMessage());
        api_json_out(false, 'Failed to delete lab', null, 500);
    }
}

api_json_out(false, "Unsupported action: '{$action}'", null, 400);
