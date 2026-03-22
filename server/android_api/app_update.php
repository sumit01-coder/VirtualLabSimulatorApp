<?php
// android_api/app_update.php
// Public app update feed (safe to call without auth).
//
// Returns latest GitHub release info + whether an update is available.

require_once __DIR__ . '/ddos_guard.php';

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

function read_bool(string $key, bool $default = false): bool
{
    if (!isset($_GET[$key])) return $default;
    $v = $_GET[$key];
    if (!is_scalar($v)) return $default;
    $v = strtolower(trim((string)$v));
    if ($v === '') return $default;
    return in_array($v, ['1', 'true', 'yes', 'y', 'on'], true);
}

function cache_path(string $name): string
{
    // Default: create/use a sibling `cache/` next to `android_api/` (i.e. `<public_html>/cache/`).
    // This is usually writable and avoids depending on deep directory layouts.
    return dirname(__DIR__) . '/cache/' . $name;
}

function http_get_json(string $url, int $timeoutSeconds = 8): ?array
{
    return http_get_json_meta($url, $timeoutSeconds)['json'] ?? null;
}

function parse_status_code(array $responseHeaders): int
{
    if (!$responseHeaders) return 0;
    $line0 = (string)$responseHeaders[0];
    if (preg_match('/\\s(\\d{3})\\s/', $line0, $m)) {
        return (int)$m[1];
    }
    return 0;
}

function find_header(array $responseHeaders, string $name): ?string
{
    $name = strtolower($name);
    foreach ($responseHeaders as $h) {
        $h = (string)$h;
        $pos = strpos($h, ':');
        if ($pos === false) continue;
        $k = strtolower(trim(substr($h, 0, $pos)));
        if ($k !== $name) continue;
        return trim(substr($h, $pos + 1));
    }
    return null;
}

function http_get_json_meta(string $url, int $timeoutSeconds = 8, ?string $etag = null): array
{
    $token = getenv('GITHUB_TOKEN') ?: '';
    $headers = "User-Agent: VirtualLabAdminUpdateCheck/1.0\r\nAccept: application/vnd.github+json\r\n";
    if ($etag) {
        $headers .= "If-None-Match: " . $etag . "\r\n";
    }
    if (is_string($token) && trim($token) !== '') {
        $headers .= "Authorization: Bearer " . trim($token) . "\r\n";
    }

    $ctx = stream_context_create([
        'http' => [
            'method' => 'GET',
            'header' => $headers,
            'timeout' => $timeoutSeconds,
            // Allow reading response body on non-2xx to improve diagnostics.
            'ignore_errors' => true,
        ],
        'ssl' => [
            'verify_peer' => true,
            'verify_peer_name' => true,
        ],
    ]);

    $raw = @file_get_contents($url, false, $ctx);
    $responseHeaders = $http_response_header ?? [];
    $status = parse_status_code($responseHeaders);
    $respEtag = find_header($responseHeaders, 'ETag');

    $decoded = null;
    if (is_string($raw) && $raw !== '') {
        $tmp = json_decode($raw, true);
        if (is_array($tmp)) $decoded = $tmp;
    }

    return [
        'status' => $status,
        'etag' => $respEtag,
        'headers' => $responseHeaders,
        'json' => $decoded,
        'raw' => is_string($raw) ? $raw : null,
    ];
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
        $contentType = strtolower((string)($a['content_type'] ?? ''));
        if (str_ends_with($name, '.apk') || $contentType === 'application/vnd.android.package-archive') {
            return $url;
        }
    }
    return null;
}

$repo = read_str('repo', 'sumit01-coder/VirtualLabSimulatorApp');
$currentVersion = normalize_tag(read_str('current_version', '0.0.0'));
$platform = strtolower(read_str('platform', 'android'));
$redirect = read_bool('redirect', false);
$target = strtolower(read_str('target', 'download')); // download|release
$noCache = read_bool('nocache', false);

$repo = trim($repo);
$parts = explode('/', $repo, 2);
if (count($parts) !== 2 || trim($parts[0]) === '' || trim($parts[1]) === '') {
    json_out(false, 'Invalid repo. Use owner/repo.', [
        'repo' => $repo,
        'platform' => $platform,
        'current_version' => $currentVersion,
        'latest' => null,
        'update_available' => false,
    ], 400);
}
$owner = trim($parts[0]);
$repoName = trim($parts[1]);

$cacheFile = cache_path('github_release_' . preg_replace('/[^a-zA-Z0-9_\\-\\.]/', '_', $repo) . '.json');
$cacheTtl = 300; // seconds

$cached = null;
$cachedRelease = null;
$cachedEtag = null;
$cachedFetchedAt = 0;
if (!$noCache && is_file($cacheFile)) {
    $mtime = @filemtime($cacheFile) ?: 0;
    if ($mtime > 0 && (time() - $mtime) < $cacheTtl) {
        $raw = @file_get_contents($cacheFile);
        $decoded = $raw ? json_decode($raw, true) : null;
        if (is_array($decoded)) $cached = $decoded;
    }
}

$release = null;
$fromCache = false;

if (is_array($cached) && isset($cached['release']) && is_array($cached['release'])) {
    $cachedRelease = $cached['release'];
    $cachedEtag = is_string($cached['etag'] ?? null) ? (string)$cached['etag'] : null;
    $cachedFetchedAt = (int)($cached['fetched_at'] ?? 0);
}

if (!$noCache && $cachedRelease && $cachedFetchedAt > 0 && (time() - $cachedFetchedAt) < $cacheTtl) {
    $release = $cachedRelease;
    $fromCache = true;
} else {
    // IMPORTANT: do not URL-encode the "/" between owner and repo (GitHub expects /repos/{owner}/{repo}).
    $meta = http_get_json_meta(
        "https://api.github.com/repos/" . rawurlencode($owner) . "/" . rawurlencode($repoName) . "/releases/latest"
        ,
        8,
        $cachedEtag
    );

    if (($meta['status'] ?? 0) === 304 && $cachedRelease) {
        $release = $cachedRelease;
        $fromCache = true;
        @mkdir(dirname($cacheFile), 0777, true);
        @file_put_contents($cacheFile, json_encode([
            'fetched_at' => time(),
            'etag' => $cachedEtag,
            'release' => $cachedRelease,
        ], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
    } else if (is_array($meta['json'] ?? null)) {
        $release = $meta['json'];
        $fromCache = false;
        @mkdir(dirname($cacheFile), 0777, true);
        @file_put_contents($cacheFile, json_encode([
            'fetched_at' => time(),
            'etag' => $meta['etag'] ?? null,
            'release' => $release,
        ], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
    } else if ($cachedRelease) {
        // Fallback to last known release even if GitHub is temporarily unavailable.
        $release = $cachedRelease;
        $fromCache = true;
    }
}

if (!is_array($release) || empty($release['tag_name'])) {
    json_out(false, 'Release info unavailable', [
        'repo' => $repo,
        'platform' => $platform,
        'current_version' => $currentVersion,
        'latest' => null,
        'update_available' => false,
        'cached' => $fromCache,
    ], 503);
}

$tag = normalize_tag((string)$release['tag_name']);
$publishedAt = (string)($release['published_at'] ?? '');
$htmlUrl = (string)($release['html_url'] ?? '');
$notes = (string)($release['body'] ?? '');
$apkUrl = find_apk_url($release);

$updateAvailable = version_compare($tag, $currentVersion, '>');

if ($redirect) {
    $redirectUrl = $target === 'release' ? $htmlUrl : ($apkUrl ?: $htmlUrl);
    if ($redirectUrl !== '') {
        header('Location: ' . $redirectUrl, true, 302);
        exit;
    }
}

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
    'cached' => $fromCache,
]);
