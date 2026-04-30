<?php
// android_api/app_update.php
// Public endpoint — returns latest GitHub release info for the app.
// No auth required. Protected by DDoS guard.
//
// GET ?current_version=1.0.0&repo=owner/repo&platform=android
// GET ?redirect=1&target=download|release   (redirects to APK or release page)

declare(strict_types=1);

if (is_file(__DIR__ . '/ddos_guard.php')) {
    require_once __DIR__ . '/ddos_guard.php';
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    http_response_code(405);
    echo json_encode(['status' => false, 'message' => 'GET required'], JSON_UNESCAPED_SLASHES);
    exit;
}

// ── Helpers ───────────────────────────────────────────────────────────────
function upd_json_out(bool $status, string $message, $data = null, int $httpStatus = 200): void
{
    http_response_code($httpStatus);
    echo json_encode(['status' => $status, 'message' => $message, 'data' => $data], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function upd_read_str(string $key, string $default = ''): string
{
    $v = $_GET[$key] ?? $default;
    if (!is_scalar($v)) return $default;
    $v = trim((string)$v);
    return $v === '' ? $default : $v;
}

function upd_read_bool(string $key, bool $default = false): bool
{
    if (!isset($_GET[$key])) return $default;
    $v = $_GET[$key];
    if (!is_scalar($v)) return $default;
    $v = strtolower(trim((string)$v));
    return in_array($v, ['1', 'true', 'yes', 'y', 'on'], true);
}

function upd_cache_path(string $name): string
{
    return dirname(__DIR__) . '/cache/' . $name;
}

function upd_parse_status_code(array $headers): int
{
    $line0 = (string)($headers[0] ?? '');
    if (preg_match('/\s(\d{3})\s/', $line0, $m)) return (int)$m[1];
    return 0;
}

function upd_find_header(array $headers, string $name): ?string
{
    $name = strtolower($name);
    foreach ($headers as $h) {
        $h   = (string)$h;
        $pos = strpos($h, ':');
        if ($pos === false) continue;
        if (strtolower(trim(substr($h, 0, $pos))) !== $name) continue;
        return trim(substr($h, $pos + 1));
    }
    return null;
}

function upd_http_get(string $url, int $timeout = 8, ?string $etag = null): array
{
    $token   = getenv('GITHUB_TOKEN') ?: '';
    $headers = "User-Agent: VirtualLabAdminUpdateCheck/1.0\r\n"
             . "Accept: application/vnd.github+json\r\n";
    if ($etag) $headers .= "If-None-Match: {$etag}\r\n";
    if (is_string($token) && trim($token) !== '') $headers .= "Authorization: Bearer " . trim($token) . "\r\n";

    $ctx = stream_context_create([
        'http' => ['method' => 'GET', 'header' => $headers, 'timeout' => $timeout, 'ignore_errors' => true],
        'ssl'  => ['verify_peer' => true, 'verify_peer_name' => true],
    ]);

    $raw         = @file_get_contents($url, false, $ctx);
    $respHeaders = $http_response_header ?? [];
    $status      = upd_parse_status_code($respHeaders);
    $respEtag    = upd_find_header($respHeaders, 'ETag');
    $decoded     = null;

    if (is_string($raw) && $raw !== '') {
        $tmp = json_decode($raw, true);
        if (is_array($tmp)) $decoded = $tmp;
    }

    return ['status' => $status, 'etag' => $respEtag, 'json' => $decoded];
}

function upd_normalize_tag(string $tag): string
{
    return ltrim(trim($tag), 'vV');
}

function upd_find_apk_url(array $release): ?string
{
    foreach ($release['assets'] ?? [] as $a) {
        if (!is_array($a)) continue;
        $name        = strtolower((string)($a['name']           ?? ''));
        $url         = (string)($a['browser_download_url']      ?? '');
        $contentType = strtolower((string)($a['content_type']   ?? ''));
        if ($url === '') continue;
        if (str_ends_with($name, '.apk') || $contentType === 'application/vnd.android.package-archive') {
            return $url;
        }
    }
    return null;
}

// ── Parse request ─────────────────────────────────────────────────────────
$repo           = upd_read_str('repo', 'sumit01-coder/VirtualLabSimulatorApp');
$currentVersion = upd_normalize_tag(upd_read_str('current_version', '0.0.0'));
$platform       = strtolower(upd_read_str('platform', 'android'));
$redirect       = upd_read_bool('redirect', false);
$target         = strtolower(upd_read_str('target', 'download'));
$noCache        = upd_read_bool('nocache', false);

$parts = explode('/', trim($repo), 2);
if (count($parts) !== 2 || trim($parts[0]) === '' || trim($parts[1]) === '') {
    upd_json_out(false, 'Invalid repo — use owner/repo format', [
        'repo' => $repo, 'platform' => $platform,
        'current_version' => $currentVersion, 'latest' => null, 'update_available' => false,
    ], 400);
}
[$owner, $repoName] = [trim($parts[0]), trim($parts[1])];

// ── Cache ─────────────────────────────────────────────────────────────────
$cacheKey  = preg_replace('/[^a-zA-Z0-9_\-\.]/', '_', $repo);
$cacheFile = upd_cache_path("github_release_{$cacheKey}.json");
$cacheTtl  = 300;

$cachedRelease  = null;
$cachedEtag     = null;
$cachedFetchedAt = 0;

if (!$noCache && is_file($cacheFile)) {
    $raw = @file_get_contents($cacheFile);
    $dec = $raw ? json_decode($raw, true) : null;
    if (is_array($dec)) {
        $cachedRelease   = $dec['release']    ?? null;
        $cachedEtag      = is_string($dec['etag'] ?? null) ? $dec['etag'] : null;
        $cachedFetchedAt = (int)($dec['fetched_at'] ?? 0);
    }
}

$release   = null;
$fromCache = false;

if (!$noCache && is_array($cachedRelease) && (time() - $cachedFetchedAt) < $cacheTtl) {
    $release   = $cachedRelease;
    $fromCache = true;
} else {
    $apiUrl = "https://api.github.com/repos/" . rawurlencode($owner) . "/" . rawurlencode($repoName) . "/releases/latest";
    $meta   = upd_http_get($apiUrl, 8, $cachedEtag);

    if (($meta['status'] ?? 0) === 304 && is_array($cachedRelease)) {
        $release   = $cachedRelease;
        $fromCache = true;
        @mkdir(dirname($cacheFile), 0755, true);
        @file_put_contents($cacheFile, json_encode([
            'fetched_at' => time(), 'etag' => $cachedEtag, 'release' => $cachedRelease,
        ], JSON_UNESCAPED_SLASHES), LOCK_EX);
    } elseif (is_array($meta['json'] ?? null)) {
        $release   = $meta['json'];
        $fromCache = false;
        @mkdir(dirname($cacheFile), 0755, true);
        @file_put_contents($cacheFile, json_encode([
            'fetched_at' => time(), 'etag' => $meta['etag'] ?? null, 'release' => $release,
        ], JSON_UNESCAPED_SLASHES), LOCK_EX);
    } elseif (is_array($cachedRelease)) {
        // GitHub temporarily unavailable — use stale cache
        $release   = $cachedRelease;
        $fromCache = true;
    }
}

if (!is_array($release) || empty($release['tag_name'])) {
    upd_json_out(false, 'Release info unavailable (GitHub may be down or repo has no releases)', [
        'repo' => $repo, 'platform' => $platform,
        'current_version' => $currentVersion, 'latest' => null,
        'update_available' => false, 'cached' => $fromCache,
    ], 503);
}

// ── Build response ────────────────────────────────────────────────────────
$tag            = upd_normalize_tag((string)$release['tag_name']);
$publishedAt    = (string)($release['published_at'] ?? '');
$htmlUrl        = (string)($release['html_url']     ?? '');
$notes          = (string)($release['body']         ?? '');
$apkUrl         = upd_find_apk_url($release);
$updateAvailable = version_compare($tag, $currentVersion, '>');

if ($redirect) {
    $redirectUrl = $target === 'release' ? $htmlUrl : ($apkUrl ?: $htmlUrl);
    if ($redirectUrl !== '') {
        header('Location: ' . $redirectUrl, true, 302);
        exit;
    }
}

upd_json_out(true, 'OK', [
    'repo'             => $repo,
    'platform'         => $platform,
    'current_version'  => $currentVersion,
    'latest'           => [
        'version'      => $tag,
        'tag'          => (string)$release['tag_name'],
        'published_at' => $publishedAt,
        'release_url'  => $htmlUrl,
        'download_url' => $apkUrl ?: $htmlUrl,
        'notes'        => $notes,
    ],
    'update_available' => $updateAvailable,
    'cached'           => $fromCache,
]);
