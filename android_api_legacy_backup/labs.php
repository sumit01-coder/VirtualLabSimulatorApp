<?php
// android_api/labs.php
require_once 'config.php';
require_once 'middleware.php';

// Verify Token
$admin = verify_admin_token();

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { exit(0); }

global $pdo;

$role = (string)($admin['role'] ?? '');
$adminDeptId = (int)($admin['department_id'] ?? 0);
$isSuper = ($role === 'super_admin');

function bad_request(string $msg) {
    header('HTTP/1.0 400 Bad Request');
    send_json_response(false, $msg);
}

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    try {
        $deptFilter = isset($_GET['department_id']) ? (int)$_GET['department_id'] : 0;
        $q = isset($_GET['q']) ? trim((string)$_GET['q']) : '';

        $where = [];
        $params = [];

        if (!$isSuper) {
            if ($adminDeptId <= 0) {
                send_json_response(true, 'Labs retrieved', []);
            }
            $where[] = "l.department_id = ?";
            $params[] = $adminDeptId;
        } elseif ($deptFilter > 0) {
            $where[] = "l.department_id = ?";
            $params[] = $deptFilter;
        }

        if ($q !== '') {
            $where[] = "(l.name LIKE ? OR l.subject LIKE ?)";
            $params[] = "%{$q}%";
            $params[] = "%{$q}%";
        }

        $sqlWhere = count($where) ? ("WHERE " . implode(" AND ", $where)) : "";

        $sql = "
            SELECT
                l.id,
                l.name,
                l.subject,
                l.topics,
                l.description,
                l.department_id,
                d.name AS department_name
            FROM labs l
            LEFT JOIN departments d ON l.department_id = d.id
            {$sqlWhere}
            ORDER BY d.name ASC, l.name ASC
            LIMIT 500
        ";

        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
        send_json_response(true, 'Labs retrieved', $stmt->fetchAll());
    } catch (PDOException $e) {
        header('HTTP/1.0 500 Internal Server Error');
        send_json_response(false, 'Database error: ' . $e->getMessage());
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $input = json_decode(file_get_contents('php://input'), true);
    if (!is_array($input)) $input = [];

    $action = (string)($input['action'] ?? '');

    if ($action === 'add' || $action === 'edit') {
        $name = trim((string)($input['name'] ?? ''));
        $description = (string)($input['description'] ?? '');
        $topics = (string)($input['topics'] ?? '');
        $subject = (string)($input['subject'] ?? '');
        $deptId = (int)($input['department_id'] ?? 0);

        if ($name === '') bad_request('Lab name is required');

        if (!$isSuper) {
            if ($adminDeptId <= 0) bad_request('Admin department missing');
            $deptId = $adminDeptId;
        } else {
            if ($deptId <= 0) bad_request('Department is required');
        }

        try {
            if ($action === 'add') {
                $stmt = $pdo->prepare("INSERT INTO labs (name, description, topics, subject, department_id) VALUES (?, ?, ?, ?, ?)");
                $stmt->execute([$name, $description, $topics, $subject, $deptId]);
                send_json_response(true, 'Lab added', ['id' => (int)$pdo->lastInsertId()]);
            } else {
                $id = (int)($input['id'] ?? 0);
                if ($id <= 0) bad_request('Missing lab id');

                if (!$isSuper) {
                    $check = $pdo->prepare("SELECT department_id FROM labs WHERE id = ? LIMIT 1");
                    $check->execute([$id]);
                    $labDept = (int)$check->fetchColumn();
                    if ($labDept !== $adminDeptId) {
                        header('HTTP/1.0 403 Forbidden');
                        send_json_response(false, 'Access denied');
                    }
                }

                $stmt = $pdo->prepare("UPDATE labs SET name=?, description=?, topics=?, subject=?, department_id=? WHERE id=?");
                $stmt->execute([$name, $description, $topics, $subject, $deptId, $id]);
                send_json_response(true, 'Lab updated');
            }
        } catch (PDOException $e) {
            header('HTTP/1.0 500 Internal Server Error');
            send_json_response(false, 'Database error: ' . $e->getMessage());
        }
    }

    if ($action === 'delete') {
        $id = (int)($input['id'] ?? 0);
        if ($id <= 0) bad_request('Missing lab id');

        try {
            if (!$isSuper) {
                $check = $pdo->prepare("SELECT department_id FROM labs WHERE id = ? LIMIT 1");
                $check->execute([$id]);
                $labDept = (int)$check->fetchColumn();
                if ($labDept !== $adminDeptId) {
                    header('HTTP/1.0 403 Forbidden');
                    send_json_response(false, 'Access denied');
                }
            }

            $stmt = $pdo->prepare("DELETE FROM labs WHERE id = ?");
            $stmt->execute([$id]);
            send_json_response(true, 'Lab deleted');
        } catch (PDOException $e) {
            header('HTTP/1.0 500 Internal Server Error');
            send_json_response(false, 'Database error: ' . $e->getMessage());
        }
    }

    bad_request('Invalid action');
}

header('HTTP/1.0 405 Method Not Allowed');
send_json_response(false, 'Unsupported method');

