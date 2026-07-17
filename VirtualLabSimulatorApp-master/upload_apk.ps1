param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseId,

    [Parameter(Mandatory = $true)]
    [string]$FilePath,

    [string]$FileName = $(Split-Path -Leaf $FilePath),

    [string]$Repo = "sumit01-coder/VirtualLabSimulatorApp"
)

$token = $env:GITHUB_TOKEN
if (-not $token -or $token.Trim().Length -lt 20) {
    throw "Missing token. Set env var GITHUB_TOKEN to a GitHub token with permission to upload release assets."
}

$headers = @{
    Authorization = "Bearer $token"
    "Content-Type" = "application/vnd.android.package-archive"
}

$uploadUrl = "https://uploads.github.com/repos/$Repo/releases/$ReleaseId/assets?name=$FileName"

if (-not (Test-Path -LiteralPath $FilePath)) {
    throw "APK not found: $FilePath"
}

Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $headers -InFile $FilePath -ContentType "application/vnd.android.package-archive"
