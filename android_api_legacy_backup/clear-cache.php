<?php
$repo = 'sumit01-coder/VirtualLabSimulatorApp';
$cacheFile = dirname(__DIR__) . '/cache/github_release_' . preg_replace('/[^a-zA-Z0-9_\\-\\.]/', '_', $repo) . '.json';
if (file_exists($cacheFile)) {
    if (unlink($cacheFile)) {
        echo "Deleted cache: $cacheFile";
    } else {
        echo "Failed to delete cache: $cacheFile";
    }
} else {
    echo "Cache file not found: $cacheFile";
}
?>
