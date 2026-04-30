<?php
// android_api/users.php
require_once 'config.php';
require_once 'middleware.php';

// Verify Token
$admin = verify_admin_token();

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { exit(0); }

global $pdo;

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    try {
        // Discover columns safely (DB schema may vary).
        // Prefer SHOW COLUMNS (MySQL/MariaDB). If it fails, we fall back to minimal fields.
        $existingCols = [];
        try {
            $colsStmt = $pdo->query("SHOW COLUMNS FROM users");
            $cols = $colsStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($cols as $c) {
                if (!empty($c['Field'])) $existingCols[$c['Field']] = true;
            }
        } catch (Throwable $e) {
            $existingCols = [];
        }

        // Support a lightweight mode for recipient pickers / broadcast lists:
        // GET /android_api/users.php?basic=1  -> returns only full_name + email
        $basic = isset($_GET['basic']) && (string)$_GET['basic'] === '1';

        if ($basic) {
            if (!empty($existingCols) && (!isset($existingCols['full_name']) || !isset($existingCols['email']))) {
                send_json_response(false, 'Database schema missing full_name/email');
            }

            $stmt = $pdo->query("SELECT full_name, email FROM users ORDER BY full_name ASC LIMIT 5000");
            send_json_response(true, 'Users retrieved', $stmt->fetchAll());
        }

        // Default: richer payload used by the app user list (admin/send_email.php compatible).
        $allowed = ['id','full_name','email','unique_id','username','role','institution','tokens','status','department','current_year','created_at'];
        $selectCols = [];

        if (!empty($existingCols)) {
            foreach ($allowed as $col) {
                if (isset($existingCols[$col])) $selectCols[] = $col;
            }
        } else {
            // If we can't inspect columns, use a conservative set that matches the web admin page.
            $selectCols = ['id','full_name','email','username','role','institution','tokens','status'];
        }

        // Ensure at least name/email exist in output.
        if (!in_array('full_name', $selectCols, true)) $selectCols[] = 'full_name';
        if (!in_array('email', $selectCols, true)) $selectCols[] = 'email';

        $order = in_array('id', $selectCols, true) ? 'id DESC' : 'full_name ASC';
        $sql = "SELECT " . implode(", ", $selectCols) . " FROM users ORDER BY {$order} LIMIT 5000";

        $stmt = $pdo->query($sql);
        send_json_response(true, 'Users retrieved', $stmt->fetchAll(PDO::FETCH_ASSOC));
    } catch (PDOException $e) {
        header('HTTP/1.0 500 Internal Server Error');
        send_json_response(false, 'Database error: ' . $e->getMessage());
    }
}
?>
