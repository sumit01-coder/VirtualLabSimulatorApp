<?php
// android_api/middleware.php

function base64url_encode($data) {
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}

function generate_jwt($payload, $secret) {
    $header = json_encode(['typ' => 'JWT', 'alg' => 'HS256']);
    $base64UrlHeader = base64url_encode($header);
    
    $base64UrlPayload = base64url_encode(json_encode($payload));
    
    $signature = hash_hmac('sha256', $base64UrlHeader . "." . $base64UrlPayload, $secret, true);
    $base64UrlSignature = base64url_encode($signature);
    
    return $base64UrlHeader . "." . $base64UrlPayload . "." . $base64UrlSignature;
}

function verify_admin_token() {
    // PHP on some servers might not populate passing Bearer tokens into $_SERVER['HTTP_AUTHORIZATION']
    $authHeader = '';
    
    // First, try to get from getallheaders (works in Apache)
    if (function_exists('getallheaders')) {
        $headers = getallheaders();
        if (isset($headers['Authorization'])) {
            $authHeader = $headers['Authorization'];
        } elseif (isset($headers['authorization'])) {
            $authHeader = $headers['authorization'];
        }
    }
    
    // Fallback for Nginx or custom setups
    if (empty($authHeader)) {
        if (isset($_SERVER['HTTP_AUTHORIZATION'])) {
            $authHeader = $_SERVER['HTTP_AUTHORIZATION'];
        } elseif (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
            $authHeader = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
        }
    }

    if (empty($authHeader) || !preg_match('/Bearer\s(\S+)/', $authHeader, $matches)) {
        header('HTTP/1.0 401 Unauthorized');
        send_json_response(false, 'No valid authorization token provided');
    }

    $token = $matches[1];
    $tokenParts = explode('.', $token);

    if (count($tokenParts) != 3) {
        header('HTTP/1.0 401 Unauthorized');
        send_json_response(false, 'Invalid token format');
    }

    $headerData = base64_decode(strtr($tokenParts[0], '-_', '+/'));
    $payloadData = base64_decode(strtr($tokenParts[1], '-_', '+/'));
    $signatureProvided = $tokenParts[2];

    $payload = json_decode($payloadData, true);

    if (!$payload) {
        header('HTTP/1.0 401 Unauthorized');
        send_json_response(false, 'Invalid token payload');
    }

    if (isset($payload['exp']) && $payload['exp'] < time()) {
        header('HTTP/1.0 401 Unauthorized');
        send_json_response(false, 'Token has expired');
    }

    $expectedSignature = base64url_encode(hash_hmac('sha256', $tokenParts[0] . "." . $tokenParts[1], API_JWT_SECRET, true));

    if (!hash_equals($expectedSignature, $signatureProvided)) {
        header('HTTP/1.0 401 Unauthorized');
        send_json_response(false, 'Invalid token signature');
    }

    return $payload; // Return decoded payload data so endpoints know who is calling
}
?>
