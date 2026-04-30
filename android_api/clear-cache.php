<?php
// android_api/clear-cache.php
// Utility: clears the GitHub release cache and OTP store.
// Protected: requires Authorization: Bearer <super_admin token>

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
}

$admin = api_require_admin_auth();
if (!api_is_super_admin($admin)) {
    api_json_out(false, 'Access denied: super_admin only', null, 403);
}

$deleted = [];
$failed  = [];

// Cache files to clear
$files = [
    'github_release_sumit01-coder_VirtualLabSimulatorApp.json' => api_cache_path('github_release_sumit01-coder_VirtualLabSimulatorApp.json'),
    'admin_otp_store.json'  => api_cache_path('admin_otp_store.json'),
    'admin_rate_limit.json' => api_cache_path('admin_rate_limit.json'),
];

// Also clear any other github_release_*.json files
$cacheDir = dirname(__DIR__) . '/cache/';
if (is_dir($cacheDir)) {
    foreach (glob($cacheDir . 'github_release_*.json') ?: [] as $f) {
        $key = basename($f);
        $files[$key] = $f;
    }
}

foreach ($files as $name => $path) {
    if (!is_file($path)) continue;
    if (@unlink($path)) {
        $deleted[] = $name;
    } else {
        $failed[] = $name;
    }
}

api_json_out(true, 'Cache cleared', [
    'deleted' => $deleted,
    'failed'  => $failed,
]);
