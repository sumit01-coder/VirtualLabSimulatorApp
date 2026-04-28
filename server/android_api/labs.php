<?php
// android_api/labs.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$pdo = api_connect_pdo();
$admin = api_require_admin_auth();

if ($method === 'GET') {
    $departmentId = (int)api_read_query_str('department_id', '0');
    $q = trim(api_read_query_str('q', ''));

    $where = [];
    $params = [];

    if ($departmentId > 0) {
        $where[] = 'l.department_id = :dep';
        $params[':dep'] = $departmentId;
    }
    if ($q !== '') {
        $where[] = '(l.name LIKE :q OR l.subject LIKE :q OR l.topics LIKE :q)';
        $params[':q'] = '%' . $q . '%';
    }

    $sql = "SELECT l.id, l.name, COALESCE(l.subject, '') AS subject, COALESCE(l.topics, '') AS topics,
                   COALESCE(l.description, '') AS description,
                   COALESCE(l.department_id, 0) AS department_id,
                   COALESCE(d.name, '') AS department_name
            FROM labs l
            LEFT JOIN departments d ON d.id = l.department_id";

    if (!empty($where)) $sql .= ' WHERE ' . implode(' AND ', $where);
    $sql .= ' ORDER BY l.name ASC';

    try {
        $st = $pdo->prepare($sql);
        $st->execute($params);
        $rows = $st->fetchAll();
        api_json_out(true, 'OK', is_array($rows) ? $rows : []);
    } catch (Throwable $e) {
        api_json_out(false, 'Failed to fetch labs', null, 500);
    }
}

if ($method !== 'POST') {
    api_json_out(false, 'Method not allowed', null, 405);
}

if (!api_is_super_admin($admin)) {
    api_json_out(false, 'Access denied (super_admin only)', null, 403);
}

$body = api_read_json_body();
$action = isset($body['action']) && is_scalar($body['action']) ? strtolower(trim((string)$body['action'])) : '';
$id = isset($body['id']) && is_scalar($body['id']) ? (int)$body['id'] : 0;
$name = isset($body['name']) && is_scalar($body['name']) ? trim((string)$body['name']) : '';
$subject = isset($body['subject']) && is_scalar($body['subject']) ? trim((string)$body['subject']) : '';
$topics = isset($body['topics']) && is_scalar($body['topics']) ? trim((string)$body['topics']) : '';
$description = isset($body['description']) && is_scalar($body['description']) ? trim((string)$body['description']) : '';
$departmentId = isset($body['department_id']) && is_scalar($body['department_id']) ? (int)$body['department_id'] : 0;

if ($action === 'add') {
    if ($name === '') api_json_out(false, 'name required', null, 400);
    try {
        $st = $pdo->prepare('INSERT INTO labs (name, subject, topics, description, department_id) VALUES (:n, :s, :t, :d, :dep)');
        $st->execute([':n' => $name, ':s' => $subject, ':t' => $topics, ':d' => $description, ':dep' => $departmentId]);
        api_json_out(true, 'Lab added', (object)[]);
    } catch (Throwable $e) {
        api_json_out(false, 'Failed to add lab', null, 500);
    }
}

if ($action === 'edit') {
    if ($id <= 0 || $name === '') api_json_out(false, 'id and name required', null, 400);
    try {
        $st = $pdo->prepare('UPDATE labs SET name = :n, subject = :s, topics = :t, description = :d, department_id = :dep WHERE id = :id');
        $st->execute([':id' => $id, ':n' => $name, ':s' => $subject, ':t' => $topics, ':d' => $description, ':dep' => $departmentId]);
        api_json_out(true, 'Lab updated', (object)[]);
    } catch (Throwable $e) {
        api_json_out(false, 'Failed to update lab', null, 500);
    }
}

if ($action === 'delete') {
    if ($id <= 0) api_json_out(false, 'id required', null, 400);
    try {
        $st = $pdo->prepare('DELETE FROM labs WHERE id = :id');
        $st->execute([':id' => $id]);
        api_json_out(true, 'Lab deleted', (object)[]);
    } catch (Throwable $e) {
        api_json_out(false, 'Failed to delete lab', null, 500);
    }
}

api_json_out(false, 'Unsupported action', null, 400);