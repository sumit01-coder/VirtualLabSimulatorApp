<?php
// android_api/users.php

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
}

$pdo = api_connect_pdo();
api_require_admin_auth();

$queries = [
    "SELECT id, full_name, email, unique_id, username, role, institution,
            COALESCE(tokens, 0) AS tokens,
            status, department, current_year, created_at
     FROM users
     ORDER BY id DESC
     LIMIT 5000",

    "SELECT id,
            COALESCE(name, username, '') AS full_name,
            COALESCE(email, '') AS email,
            COALESCE(unique_id, '') AS unique_id,
            COALESCE(username, '') AS username,
            COALESCE(role, 'student') AS role,
            COALESCE(institution, '') AS institution,
            COALESCE(tokens, 0) AS tokens,
            COALESCE(status, 'active') AS status,
            COALESCE(department, '') AS department,
            COALESCE(current_year, '') AS current_year,
            COALESCE(created_at, '') AS created_at
     FROM users
     ORDER BY id DESC
     LIMIT 5000",
];

foreach ($queries as $sql) {
    try {
        $st = $pdo->query($sql);
        $rows = $st->fetchAll();
        api_json_out(true, 'OK', is_array($rows) ? $rows : []);
    } catch (Throwable $e) {}
}

api_json_out(false, 'Failed to fetch users', null, 500);