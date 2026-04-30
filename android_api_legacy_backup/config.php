<?php
// android_api/config.php
require_once dirname(__DIR__) . '/includes/db.php';

// JWT Secret Key
define('API_JWT_SECRET', 'VirtualLab_Android_App_Ultra_Secret_Key_2026!@#');

// Helper function to send JSON responses
function send_json_response($status, $message, $data = null) {
    header('Content-Type: application/json');
    echo json_encode(['status' => $status, 'message' => $message, 'data' => $data]);
    exit();
}
?>
