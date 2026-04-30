<?php
$url = "https://api.github.com/repos/sumit01-coder/VirtualLabSimulatorApp/releases/latest";
$ctx = stream_context_create([
    'http' => [
        'method' => 'GET',
        'header' => "User-Agent: PHP-Test\r\nAccept: application/vnd.github+json\r\n",
        'timeout' => 8,
    ]
]);
$raw = file_get_contents($url, false, $ctx);
if ($raw === false) {
    $error = error_get_last();
    echo "FAILED: " . print_r($error, true);
} else {
    echo "SUCCESS: " . substr($raw, 0, 100) . "...";
}
?>
