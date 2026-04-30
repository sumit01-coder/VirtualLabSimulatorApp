<?php
/**
 * android_api/test_api_syntax.php
 *
 * CLI-only helper to lint all PHP files in this folder using `php -l`.
 *
 * Usage:
 *   php android_api/test_api_syntax.php
 *   php android_api/test_api_syntax.php --json
 */

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    header('Content-Type: text/plain; charset=utf-8');
    echo "This script is intended to be run from the command line.\n";
    echo "Example: php android_api/test_api_syntax.php\n";
    exit(1);
}

$args = $argv ?? [];
$jsonOutput = in_array('--json', $args, true);

$dir = __DIR__;
$self = realpath(__FILE__) ?: __FILE__;

$files = glob($dir . DIRECTORY_SEPARATOR . '*.php') ?: [];
sort($files, SORT_NATURAL | SORT_FLAG_CASE);

$results = [];
$okCount = 0;
$failCount = 0;

foreach ($files as $file) {
    $real = realpath($file) ?: $file;
    if ($real === $self) {
        continue;
    }

    $cmd = escapeshellarg(PHP_BINARY) . ' -l ' . escapeshellarg($real);
    $outputLines = [];
    $exitCode = 0;
    exec($cmd, $outputLines, $exitCode);

    $output = trim(implode("\n", $outputLines));
    $ok = ($exitCode === 0);

    if ($ok) {
        $okCount++;
    } else {
        $failCount++;
    }

    $results[] = [
        'file' => basename($real),
        'ok' => $ok,
        'exit_code' => $exitCode,
        'output' => $output,
    ];
}

if ($jsonOutput) {
    echo json_encode(
        [
            'ok' => ($failCount === 0),
            'checked' => count($results),
            'passed' => $okCount,
            'failed' => $failCount,
            'results' => $results,
        ],
        JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES
    ) . "\n";
    exit($failCount === 0 ? 0 : 2);
}

echo "PHP syntax check: android_api\n";
echo "Checked: " . count($results) . " | Passed: " . $okCount . " | Failed: " . $failCount . "\n\n";

foreach ($results as $r) {
    $prefix = $r['ok'] ? '[OK]   ' : '[FAIL] ';
    echo $prefix . $r['file'] . "\n";
    if (!$r['ok']) {
        echo "  " . str_replace("\n", "\n  ", $r['output']) . "\n";
    }
}

exit($failCount === 0 ? 0 : 2);

