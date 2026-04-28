<?php
// android_api/departments.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$pdo = api_connect_pdo();
$admin = api_require_admin_auth();

if ($method === 'GET') {
    try {
        $st = $pdo->query("SELECT id, name, COALESCE(description, '') AS description, COALESCE(icon_class, '') AS icon_class FROM departments ORDER BY name ASC");
        $rows = $st->fetchAll();
        api_json_out(true, 'OK', is_array($rows) ? $rows : []);
    } catch (Throwable $e) {
        api_json_out(false, 'Failed to fetch departments', null, 500);
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
$desc = isset($body['description']) && is_scalar($body['description']) ? trim((string)$body['description']) : '';
$icon = isset($body['icon_class']) && is_scalar($body['icon_class']) ? trim((string)$body['icon_class']) : '';

if ($action === 'add') {
    if ($name === '') api_json_out(false, 'name required', null, 400);
    try {
        $st = $pdo->prepare('INSERT INTO departments (name, description, icon_class) VALUES (:n, :d, :i)');
        $st->execute([':n' => $name, ':d' => $desc, ':i' => $icon]);
        api_json_out(true, 'Department added', (object)[]);
    } catch (Throwable $e) {
        api_json_out(false, 'Failed to add department', null, 500);
    }
}

if ($action === 'edit') {
    if ($id <= 0 || $name === '') api_json_out(false, 'id and name required', null, 400);
    try {
        $st = $pdo->prepare('UPDATE departments SET name = :n, description = :d, icon_class = :i WHERE id = :id');
        $st->execute([':id' => $id, ':n' => $name, ':d' => $desc, ':i' => $icon]);
        api_json_out(true, 'Department updated', (object)[]);
    } catch (Throwable $e) {
        api_json_out(false, 'Failed to update department', null, 500);
    }
}

if ($action === 'delete') {
    if ($id <= 0) api_json_out(false, 'id required', null, 400);
    try {
        $st = $pdo->prepare('DELETE FROM departments WHERE id = :id');
        $st->execute([':id' => $id]);
        api_json_out(true, 'Department deleted', (object)[]);
    } catch (Throwable $e) {
        api_json_out(false, 'Failed to delete department', null, 500);
    }
}

api_json_out(false, 'Unsupported action', null, 400);