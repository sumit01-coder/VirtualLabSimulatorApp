<?php
// android_api/tickets.php
require_once 'config.php';
require_once 'middleware.php';

// Verify Token
$admin = verify_admin_token();

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { exit(0); }

global $pdo;

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    $statusFilter = $_GET['status'] ?? 'all';
    
    try {
        $sql = "SELECT t.id, t.subject, t.status, t.created_at, u.full_name as sender_name, u.email as sender_email
                FROM support_tickets t
                LEFT JOIN users u ON t.student_id = u.id ";
                
        if ($statusFilter !== 'all') {
            $sql .= " WHERE t.status = :status ";
        }
        
        $sql .= " ORDER BY FIELD(t.status, 'open', 'pending', 'closed'), t.created_at DESC LIMIT 100";
        
        $stmt = $pdo->prepare($sql);
        if ($statusFilter !== 'all') {
            $stmt->bindValue(':status', $statusFilter);
        }
        $stmt->execute();
        
        $tickets = $stmt->fetchAll();
        send_json_response(true, 'Tickets retrieved', $tickets);
    } catch (PDOException $e) {
        header('HTTP/1.0 500 Internal Server Error');
        send_json_response(false, 'Database error: ' . $e->getMessage());
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $input = json_decode(file_get_contents('php://input'), true);
    $action = $input['action'] ?? '';
    $ticket_id = $input['ticket_id'] ?? 0;

    if ($action === 'close' && $ticket_id) {
        try {
            $stmt = $pdo->prepare("UPDATE support_tickets SET status = 'closed', updated_at = NOW() WHERE id = ?");
            $stmt->execute([$ticket_id]);
            send_json_response(true, 'Ticket closed successfully');
        } catch (PDOException $e) {
            header('HTTP/1.0 500 Internal Server Error');
            send_json_response(false, 'Database error: ' . $e->getMessage());
        }
    } else {
        header('HTTP/1.0 400 Bad Request');
        send_json_response(false, 'Invalid action or missing ticket ID');
    }
}
?>
