<?php
// android_api/practicals.php
// GET ?limit=N  — list all practicals with lab/dept info + resolved URLs
// Requires: Authorization: Bearer <token>

declare(strict_types=1);
require_once __DIR__ . '/_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    api_json_out(false, 'GET required', null, 405);
}

$pdo   = api_connect_pdo();
$admin = api_require_admin_auth();

// ── Query params ──────────────────────────────────────────────────────────
$limit    = api_read_int('limit', 200, 1, 5000);
$labId    = api_read_int('lab_id', 0, 0);
$deptId   = api_read_int('department_id', 0, 0);
$q        = trim(api_read_query_str('q', ''));
$isSuper  = api_is_super_admin($admin);

// ── Build WHERE clause ────────────────────────────────────────────────────
$where  = ["TRIM(COALESCE(p.title, '')) <> ''"];
$params = [];

if ($labId > 0) {
    $where[]        = 'p.lab_id = :lab';
    $params[':lab'] = $labId;
}

if ($deptId > 0) {
    $where[]         = 'l.department_id = :dept';
    $params[':dept'] = $deptId;
} elseif (!$isSuper) {
    // Non-super admin: restrict to own department if token carries it
    $tokenDeptId = (int)($admin['department_id'] ?? 0);
    if ($tokenDeptId > 0) {
        $where[]         = 'l.department_id = :dept';
        $params[':dept'] = $tokenDeptId;
    }
}

if ($q !== '') {
    $where[]      = '(p.title LIKE :q OR p.overview LIKE :q)';
    $params[':q'] = '%' . $q . '%';
}

$whereSql = 'WHERE ' . implode(' AND ', $where);

// ── Main query ────────────────────────────────────────────────────────────
$sql = "
    SELECT
        p.id,
        p.title,
        p.lab_id,
        COALESCE(l.name,  '') AS lab_name,
        COALESCE(d.name,  '') AS dept_name,
        COALESCE(p.overview,           '') AS overview,
        COALESCE(p.objective,          '') AS objective,
        COALESCE(p.materials_required, '') AS materials_required,
        COALESCE(p.`procedure`,        '') AS `procedure`,
        COALESCE(p.program_code,       '') AS program_code,
        COALESCE(p.program_output,     '') AS program_output,
        COALESCE(p.code_description,   '') AS code_description,
        COALESCE(p.simulator_link,     '') AS simulator_link,
        COALESCE(p.figure_path,        '') AS figure_path
    FROM practicals p
    INNER JOIN labs l        ON l.id = p.lab_id
    INNER JOIN departments d ON d.id = l.department_id
    {$whereSql}
    ORDER BY d.name ASC, l.name ASC, p.id DESC
    LIMIT :lim
";

try {
    $st = $pdo->prepare($sql);
    foreach ($params as $k => $v) {
        $st->bindValue($k, $v);
    }
    $st->bindValue(':lim', $limit, PDO::PARAM_INT);
    $st->execute();
    $rows = $st->fetchAll();
} catch (Throwable $e) {
    error_log('practicals query failed: ' . $e->getMessage());
    api_json_out(false, 'Database error', null, 500);
}

// ── Resolve URLs ──────────────────────────────────────────────────────────
$https  = $_SERVER['HTTPS'] ?? '';
$scheme = (is_string($https) && strtolower($https) !== 'off' && $https !== '') ? 'https' : 'http';
$host   = is_string($_SERVER['HTTP_HOST'] ?? null) ? trim((string)$_SERVER['HTTP_HOST']) : '';
$base   = $host !== '' ? "{$scheme}://{$host}" : '';

$uploadPrefix = $base !== '' ? "{$base}/assets/uploads/"    : '';
$simPrefix    = $base !== '' ? "{$base}/assets/simulators/" : '';

foreach ($rows as &$p) {
    // Figure paths → full URLs
    $figPath = is_string($p['figure_path'] ?? null) ? trim((string)$p['figure_path']) : '';
    $figs    = $figPath !== '' ? array_filter(array_map('trim', explode(',', $figPath))) : [];
    $figUrls = [];
    foreach ($figs as $f) {
        $figUrls[] = $uploadPrefix !== '' ? ($uploadPrefix . ltrim($f, '/')) : $f;
    }
    $p['figure_urls'] = array_values($figUrls);

    // Simulator link → full URL
    $sim = is_string($p['simulator_link'] ?? null) ? trim((string)$p['simulator_link']) : '';
    if ($sim !== '' && $simPrefix !== '' && !preg_match('#^https?://#i', $sim)) {
        $p['simulator_url'] = $simPrefix . ltrim($sim, '/');
    } else {
        $p['simulator_url'] = $sim;
    }

    // Remove raw figure_path from output (clients use figure_urls)
    unset($p['figure_path']);
}
unset($p);

api_json_out(true, 'OK', $rows);
