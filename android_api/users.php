<?php
// android_api/users.php
// GET  ?basic=1  — lightweight list (full_name + email only)
// GET            — full user list for admin panel
// Requires: Authorization: Bearer <token>

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
}

$pdo = api_connect_pdo();
api_require_admin_auth();

$basic = api_read_bool($_GET['basic'] ?? null, false);

if ($basic) {
    // Lightweight mode: just name + email (for broadcast / picker UIs)
    $queries = [
        "SELECT full_name, email FROM users ORDER BY full_name ASC LIMIT 5000",
        "SELECT COALESCE(name, username, '') AS full_name, COALESCE(email,'') AS email FROM users ORDER BY full_name ASC LIMIT 5000",
    ];
    foreach ($queries as $sql) {
        try {
            $st   = $pdo->query($sql);
            $rows = $st->fetchAll();
            api_json_out(true, 'OK', is_array($rows) ? $rows : []);
        } catch (Throwable $e) {}
    }
    api_json_out(false, 'Failed to fetch users', null, 500);
}

// Full user list — try with the expected column set first, then gracefully degrade
$queries = [
    "SELECT id,
            COALESCE(full_name,     '') AS full_name,
            COALESCE(email,         '') AS email,
            COALESCE(unique_id,     '') AS unique_id,
            COALESCE(username,      '') AS username,
            COALESCE(role,          'student') AS role,
            COALESCE(institution,   '') AS institution,
            COALESCE(tokens,         0) AS tokens,
            COALESCE(status,        'active') AS status,
            COALESCE(department,    '') AS department,
            COALESCE(current_year,  '') AS current_year,
            COALESCE(created_at,    '') AS created_at
     FROM users
     ORDER BY id DESC
     LIMIT 5000",

    // Fallback for schemas where full_name is stored as 'name'
    "SELECT id,
            COALESCE(name, username, '') AS full_name,
            COALESCE(email,          '') AS email,
            COALESCE(unique_id,      '') AS unique_id,
            COALESCE(username,       '') AS username,
            COALESCE(role,           'student') AS role,
            COALESCE(institution,    '') AS institution,
            COALESCE(tokens,          0) AS tokens,
            COALESCE(status,         'active') AS status,
            COALESCE(department,     '') AS department,
            COALESCE(current_year,   '') AS current_year,
            COALESCE(created_at,     '') AS created_at
     FROM users
     ORDER BY id DESC
     LIMIT 5000",

    // Minimal fallback
    "SELECT id, COALESCE(username,'') AS full_name, COALESCE(email,'') AS email
     FROM users ORDER BY id DESC LIMIT 5000",
];

foreach ($queries as $sql) {
    try {
        $st   = $pdo->query($sql);
        $rows = $st->fetchAll();
        api_json_out(true, 'OK', is_array($rows) ? $rows : []);
    } catch (Throwable $e) {}
}

api_json_out(false, 'Failed to fetch users', null, 500);