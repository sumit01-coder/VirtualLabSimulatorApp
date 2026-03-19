<?php
// android_api/app_update.php
// Public app update feed (safe to call without auth).
//
// Returns latest GitHub release info + whether an update is available.

header('Content-Type: application/json; charset=utf-8');
header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
header("Pragma: no-cache");
header("X-Content-Type-Options: nosniff");

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    http_response_code(405);
    echo json_encode(['status' => false, 'message' => 'GET required'], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function json_out(bool $status, string $message, $data = null, int $httpStatus = 200): void
{
    http_response_code($httpStatus);
    echo json_encode(['status' => $status, 'message' => $message, 'data' => $data], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function read_str(string $key, string $default = ''): string
{
    $v = $_GET[$key] ?? $default;
    if (!is_scalar($v)) return $default;
    $v = trim((string)$v);
    return $v === '' ? $default : $v;
}

function cache_path(string $name): string
{
    return dirname(__DIR__, 3) . '/cache/' . $name;
}

function http_get_json(string $url, int $timeoutSeconds = 8): ?array
{
    $ctx = stream_context_create([
        'http' => [
            'method' => 'GET',
            'header' => "User-Agent: VirtualLabAdminUpdateCheck/1.0\r\nAccept: application/vnd.github+json\r\n",
            'timeout' => $timeoutSeconds,
        ],
        'ssl' => [
            'verify_peer' => true,
            'verify_peer_name' => true,
        ],
    ]);

    $raw = @file_get_contents($url, false, $ctx);
    if ($raw === false) return null;
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : null;
}

function normalize_tag(string $tag): string
{
    $tag = trim($tag);
    $tag = ltrim($tag, "vV");
    return $tag;
}

function find_apk_url(array $release): ?string
{
    $assets = $release['assets'] ?? null;
    if (!is_array($assets)) return null;
    foreach ($assets as $a) {
        if (!is_array($a)) continue;
        $name = strtolower((string)($a['name'] ?? ''));
        $url = (string)($a['browser_download_url'] ?? '');
        if ($url === '') continue;
        if (str_ends_with($name, '.apk') || str_contains($name, 'apk')) {
            return $url;
        }
    }
    return null;
}

$repo = read_str('repo', 'sumit01-coder/VirtualLabSimulatorApp');
$currentVersion = normalize_tag(read_str('current_version', '0.0.0'));
$platform = strtolower(read_str('platform', 'android'));

$cacheFile = cache_path('github_release_' . preg_replace('/[^a-zA-Z0-9_\\-\\.]/', '_', $repo) . '.json');
$cacheTtl = 300; // seconds

$cached = null;
if (is_file($cacheFile)) {
    $mtime = @filemtime($cacheFile) ?: 0;
    if ($mtime > 0 && (time() - $mtime) < $cacheTtl) {
        $raw = @file_get_contents($cacheFile);
        $decoded = $raw ? json_decode($raw, true) : null;
        if (is_array($decoded)) $cached = $decoded;
    }
}

$release = $cached;
if (!$release) {
    $release = http_get_json("https://api.github.com/repos/" . rawurlencode($repo) . "/releases/latest");
    if (is_array($release)) {
        @file_put_contents($cacheFile, json_encode($release, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
    }
}

if (!is_array($release) || empty($release['tag_name'])) {
    json_out(true, 'Release info unavailable', [
        'repo' => $repo,
        'platform' => $platform,
        'current_version' => $currentVersion,
        'latest' => null,
        'update_available' => false,
    ]);
}

$tag = normalize_tag((string)$release['tag_name']);
$publishedAt = (string)($release['published_at'] ?? '');
$htmlUrl = (string)($release['html_url'] ?? '');
$notes = (string)($release['body'] ?? '');
$apkUrl = find_apk_url($release);

$updateAvailable = version_compare($tag, $currentVersion, '>');

json_out(true, 'OK', [
    'repo' => $repo,
    'platform' => $platform,
    'current_version' => $currentVersion,
    'latest' => [
        'version' => $tag,
        'tag' => (string)$release['tag_name'],
        'published_at' => $publishedAt,
        'release_url' => $htmlUrl,
        'download_url' => $apkUrl ?: $htmlUrl,
        'notes' => $notes,
    ],
    'update_available' => $updateAvailable,
]);

