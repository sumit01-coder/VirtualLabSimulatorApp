<?php
// android_api/departments.php
require_once 'config.php';
require_once 'middleware.php';

// Verify Token
$admin = verify_admin_token();

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { exit(0); }

global $pdo;

function bad_request(string $msg) {
    header('HTTP/1.0 400 Bad Request');
    send_json_response(false, $msg);
}

try {
    $role = (string)($admin['role'] ?? '');
    $deptId = (int)($admin['department_id'] ?? 0);
    $isSuper = ($role === 'super_admin');

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        if ($isSuper) {
            $stmt = $pdo->query("SELECT id, name, description, icon_class FROM departments ORDER BY name ASC");
            send_json_response(true, 'Departments retrieved', $stmt->fetchAll());
        }

        if ($deptId <= 0) {
            send_json_response(true, 'Departments retrieved', []);
        }

        $stmt = $pdo->prepare("SELECT id, name, description, icon_class FROM departments WHERE id = ? LIMIT 1");
        $stmt->execute([$deptId]);
        $row = $stmt->fetch();
        $data = $row ? [$row] : [];
        send_json_response(true, 'Departments retrieved', $data);
    }

    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        if (!$isSuper) {
            header('HTTP/1.0 403 Forbidden');
            send_json_response(false, 'Access denied');
        }

        $input = json_decode(file_get_contents('php://input'), true);
        if (!is_array($input)) $input = [];

        $action = (string)($input['action'] ?? '');

        if ($action === 'add') {
            $name = trim((string)($input['name'] ?? ''));
            $description = (string)($input['description'] ?? '');
            $iconClass = (string)($input['icon_class'] ?? '');
            if ($name === '') bad_request('Department name is required');

            $stmt = $pdo->prepare("INSERT INTO departments (name, description, icon_class) VALUES (?, ?, ?)");
            $stmt->execute([$name, $description, $iconClass]);
            send_json_response(true, 'Department added', ['id' => (int)$pdo->lastInsertId()]);
        }

        if ($action === 'edit') {
            $id = (int)($input['id'] ?? 0);
            $name = trim((string)($input['name'] ?? ''));
            $description = (string)($input['description'] ?? '');
            $iconClass = (string)($input['icon_class'] ?? '');
            if ($id <= 0) bad_request('Missing department id');
            if ($name === '') bad_request('Department name is required');

            $stmt = $pdo->prepare("UPDATE departments SET name=?, description=?, icon_class=? WHERE id=?");
            $stmt->execute([$name, $description, $iconClass, $id]);
            send_json_response(true, 'Department updated');
        }

        if ($action === 'delete') {
            $id = (int)($input['id'] ?? 0);
            if ($id <= 0) bad_request('Missing department id');

            // Prevent delete if labs exist under this department (same as web admin page).
            $check = $pdo->prepare("SELECT COUNT(*) FROM labs WHERE department_id = ?");
            $check->execute([$id]);
            if ((int)$check->fetchColumn() > 0) {
                header('HTTP/1.0 409 Conflict');
                send_json_response(false, 'Department is in use by labs');
            }

            $stmt = $pdo->prepare("DELETE FROM departments WHERE id = ?");
            $stmt->execute([$id]);
            send_json_response(true, 'Department deleted');
        }

        bad_request('Invalid action');
    }

    header('HTTP/1.0 405 Method Not Allowed');
    send_json_response(false, 'Unsupported method');
} catch (PDOException $e) {
    header('HTTP/1.0 500 Internal Server Error');
    send_json_response(false, 'Database error: ' . $e->getMessage());
}
