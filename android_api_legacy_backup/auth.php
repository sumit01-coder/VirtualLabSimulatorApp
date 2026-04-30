<?php
// android_api/auth.php
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
$password = $input['password'] ?? '';

if (empty($username) || empty($password)) {
    send_json_response(false, 'Username and password are required');
}

try {
    global $pdo; // From includes/db.php
    $stmt = $pdo->prepare("SELECT id, username, email, password, role, department_id FROM admins WHERE username = ? LIMIT 1");
    $stmt->execute([$username]);
    $admin = $stmt->fetch();

    $mask_email_address = function (string $email): string {
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return 'your email address';
        }

        [$local, $domain] = explode('@', $email, 2);
        $local_length = strlen($local);

        if ($local_length <= 2) {
            $masked_local = substr($local, 0, 1) . str_repeat('*', max(1, $local_length - 1));
        } else {
            $masked_local = substr($local, 0, 1) . str_repeat('*', $local_length - 2) . substr($local, -1);
        }

        $domain_parts = explode('.', $domain);
        $domain_root = $domain_parts[0] ?? '';
        $domain_suffix = count($domain_parts) > 1 ? '.' . implode('.', array_slice($domain_parts, 1)) : '';

        if ($domain_root === '') {
            return $masked_local . '@' . $domain;
        }

        $masked_root = substr($domain_root, 0, 1) . str_repeat('*', max(1, strlen($domain_root) - 1));
        return $masked_local . '@' . $masked_root . $domain_suffix;
    };

    if ($admin && password_verify($password, $admin['password'])) {
        // Check if 2FA is enabled
        $is_2fa_enabled = false;
        try {
            $stmt2fa = $pdo->prepare("SELECT setting_value FROM settings WHERE setting_key = 'admin_email_2fa' LIMIT 1");
            $stmt2fa->execute();
            $is_2fa_enabled = $stmt2fa->fetchColumn() === '1';
        } catch (PDOException $e) {
            // Ignore, default to false
        }

        if ($is_2fa_enabled) {
            $admin_email = trim($admin['email'] ?? '');
            if (!filter_var($admin_email, FILTER_VALIDATE_EMAIL)) {
                send_json_response(false, '2FA is enabled but email is missing or invalid.');
            }

            // Generate OTP
            $otp = random_int(100000, 999999);
            
            // Require PHPMailer
            $root_path = dirname(__DIR__); 
            $phpmailer_path = $root_path . '/PHPMailer-6.10.0/PHPMailer-6.10.0/src/';
            if (!file_exists($phpmailer_path . 'PHPMailer.php')) {
                $phpmailer_path = $root_path . '/PHPMailer-6.10.0/src/';
            }
            
            require_once $phpmailer_path . 'Exception.php';
            require_once $phpmailer_path . 'PHPMailer.php';
            require_once $phpmailer_path . 'SMTP.php';
            
            // SMTP Config (from admin/index.php or config)
            if(!defined('SMTP_HOST')) define('SMTP_HOST', 'smtp.hostinger.com'); 
            if(!defined('SMTP_USER')) define('SMTP_USER', 'lab@virtuallabsimulator.com');
            if(!defined('SMTP_PASS')) define('SMTP_PASS', 'Sumit6108894!'); 
            if(!defined('SMTP_PORT')) define('SMTP_PORT', 465); 
            if(!defined('SMTP_SECURE')) define('SMTP_SECURE', \PHPMailer\PHPMailer\PHPMailer::ENCRYPTION_SMTPS);

            $mail = new \PHPMailer\PHPMailer\PHPMailer(true);
            try {
                $mail->isSMTP();
                $mail->Host       = SMTP_HOST; 
                $mail->SMTPAuth   = true; 
                $mail->Username   = SMTP_USER; 
                $mail->Password   = SMTP_PASS; 
                $mail->SMTPSecure = SMTP_SECURE; 
                $mail->Port       = SMTP_PORT;

                $mail->setFrom(SMTP_USER, 'Virtual Lab Security');
                $mail->addAddress($admin_email, $admin['username']);
                $mail->isHTML(true);
                
                $mail->Subject = 'Admin Login Verification Code';
                $mail->Body    = "<div style='font-family:sans-serif; color:#333; line-height:1.6; padding:20px; border:1px solid #ddd; max-width:600px;'>
                    <h2 style='color:#0b67ff;'>Admin Login Verification</h2>
                    <p>Hello <strong>" . htmlspecialchars($admin['username'], ENT_QUOTES, 'UTF-8') . "</strong>,</p>
                    <p>Your one-time login code is:</p>
                    <div style='background:#f8f9fa; padding:15px; text-align:center; font-size:28px; font-weight:bold; letter-spacing:8px; border-radius:8px; margin:20px 0;'>
                        " . $otp . "
                    </div>
                    <p style='color:#dc3545;'>This code expires in 5 minutes.</p>
                </div>";

                $mail->send();

                // Create Temp Token for Verification
                $tempPayload = [
                    'temp_admin_id' => $admin['id'],
                    'temp_username' => $admin['username'],
                    'temp_email' => $admin['email'],
                    'temp_role' => $admin['role'],
                    'temp_department_id' => $admin['department_id'],
                    'otp_hash' => password_hash((string) $otp, PASSWORD_DEFAULT),
                    'iat' => time(),
                    'exp' => time() + 300 // 5 minutes
                ];
                $tempToken = generate_jwt($tempPayload, API_JWT_SECRET);

                send_json_response(true, 'Verification code sent to email', [
                    'require_2fa' => true,
                    'temp_token' => $tempToken,
                    'masked_email' => $mask_email_address($admin_email),
                    'otp_expires_in' => 300,
                    'resend_cooldown' => 30
                ]);

            } catch (\Exception $e) {
                send_json_response(false, 'Could not send verification code.');
            }
        } else {
            // No 2FA required - normal login
            $payload = [
                'admin_id' => $admin['id'],
                'username' => $admin['username'],
                'role' => $admin['role'],
                'department_id' => $admin['department_id'],
                'iat' => time(),
                'exp' => time() + (86400 * 7) // Valid for 7 days
            ];

            $token = generate_jwt($payload, API_JWT_SECRET);

            send_json_response(true, 'Login successful', [
                'require_2fa' => false,
                'token' => $token,
                'admin' => [
                    'id' => $admin['id'],
                    'username' => $admin['username'],
                    'email' => $admin['email'],
                    'role' => $admin['role']
                ]
            ]);
        }
    } else {
        header('HTTP/1.0 401 Unauthorized');
        send_json_response(false, 'Invalid username or password');
    }
} catch (PDOException $e) {
    header('HTTP/1.0 500 Internal Server Error');
    send_json_response(false, 'Database error: ' . $e->getMessage());
}
?>
