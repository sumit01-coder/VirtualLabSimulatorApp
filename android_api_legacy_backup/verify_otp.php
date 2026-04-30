<?php
// android_api/verify_otp.php
require_once 'config.php';
require_once 'middleware.php';

// Allow from any origin (Adjust for production)
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    // Handle preflight requests
    exit(0);
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json_response(false, 'Invalid Request Method - Only POST allowed');
}

// Get JSON input
$input = json_decode(file_get_contents('php://input'), true);

$username = $input['username'] ?? '';
$temp_token = $input['temp_token'] ?? '';
$otp = $input['otp'] ?? '';

if (empty($username) || empty($temp_token) || empty($otp)) {
    send_json_response(false, 'Username, temporary token, and verification code are required');
}

if (!preg_match('/^\d{6}$/', $otp)) {
    send_json_response(false, 'Enter a valid 6-digit verification code.');
}

try {
    // Decode temp token manually (not via middleware since it's not a bearer)
    $tokenParts = explode('.', $temp_token);

    if (count($tokenParts) != 3) {
        send_json_response(false, 'Invalid token format');
    }

    $payloadData = base64_decode(strtr($tokenParts[1], '-_', '+/'));
    $signatureProvided = $tokenParts[2];
    $payload = json_decode($payloadData, true);

    if (!$payload || !isset($payload['temp_username']) || $payload['temp_username'] !== $username) {
        send_json_response(false, 'Invalid temporary token');
    }

    if (isset($payload['exp']) && $payload['exp'] < time()) {
        send_json_response(false, 'Verification session has expired. Please log in again.');
    }

    $expectedSignature = base64url_encode(hash_hmac('sha256', $tokenParts[0] . "." . $tokenParts[1], API_JWT_SECRET, true));

    if (!hash_equals($expectedSignature, $signatureProvided)) {
        send_json_response(false, 'Invalid token signature');
    }
    
    // Verify OTP hash
    if (!password_verify((string) $otp, $payload['otp_hash'])) {
        send_json_response(false, 'Invalid verification code');
    }

    // OTP verified successfully, generate actual JWT token
    $finalPayload = [
        'admin_id' => $payload['temp_admin_id'],
        'username' => $payload['temp_username'],
        'role' => $payload['temp_role'],
        'department_id' => $payload['temp_department_id'],
        'iat' => time(),
        'exp' => time() + (86400 * 7) // Valid for 7 days
    ];

    $token = generate_jwt($finalPayload, API_JWT_SECRET);

    send_json_response(true, 'Login successful', [
        'token' => $token,
        'admin' => [
            'id' => $payload['temp_admin_id'],
            'username' => $payload['temp_username'],
            'email' => $payload['temp_email'],
            'role' => $payload['temp_role']
        ]
    ]);

} catch (Exception $e) {
    header('HTTP/1.0 500 Internal Server Error');
    send_json_response(false, 'Server error: ' . $e->getMessage());
}
?>
