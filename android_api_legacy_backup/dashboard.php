<?php
// android_api/dashboard.php
require_once 'config.php';
require_once 'middleware.php';

// Verify Token
$admin = verify_admin_token();

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { exit(0); }

try {
    global $pdo;
    
    // Fetch stats
    $depts = $pdo->query("SELECT COUNT(*) FROM departments")->fetchColumn();
    $labs = $pdo->query("SELECT COUNT(*) FROM labs")->fetchColumn();
    $practicals = $pdo->query("SELECT COUNT(*) FROM practicals")->fetchColumn();
    $users = $pdo->query("SELECT COUNT(*) FROM users")->fetchColumn();
    $letters = $pdo->query("SELECT COUNT(*) FROM verified_letters")->fetchColumn();
    
    // Active Support Tickets
    $active_tickets = $pdo->query("SELECT COUNT(*) FROM support_tickets WHERE status != 'closed'")->fetchColumn();

    $stats = [
        'departments' => (int)$depts,
        'labs' => (int)$labs,
        'practicals' => (int)$practicals,
        'users' => (int)$users,
        'verified_letters' => (int)$letters,
        'active_tickets' => (int)$active_tickets
    ];

    send_json_response(true, 'Dashboard stats retrieved', $stats);
} catch (PDOException $e) {
    header('HTTP/1.0 500 Internal Server Error');
    send_json_response(false, 'Database error: ' . $e->getMessage());
}
?>
