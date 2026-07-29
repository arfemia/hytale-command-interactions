# Build the InteractionCommandsExamples.zip from examples/InteractionCommandsExamples/.
#
# Zips with forward-slash entries AND an explicit directory entry for every ancestor
# path (Java's ZipFileSystem.isDirectory() returns false without them, so Hytale's
# I18nModule.loadMessagesFromPack would skip Server/Languages and its .lang file).
# Never use Compress-Archive: it writes backslash separators Hytale silently drops
# on Windows. Mirrors content-packs/bounty-contracts-pack/build.ps1.
#
#   .\build-example-pack.ps1                  # build, then install if a Mods folder is known
#   .\build-example-pack.ps1 -Install:$false  # build only, no copy
#   .\build-example-pack.ps1 -ModsDir <path>  # build + install into an explicit folder
param(
    [bool]$Install = $true,
    [string]$ModsDir = $env:HYTALE_MODS_DIR
)
$ErrorActionPreference = 'Stop'

$examplesDir = $PSScriptRoot
$pack        = Join-Path $examplesDir 'InteractionCommandsExamples'
$PackName    = 'InteractionCommandsExamples'

$version = (Get-Content (Join-Path $pack 'manifest.json') -Raw | ConvertFrom-Json).Version
if (-not $version) { throw 'manifest.json is missing a Version field' }

# The maintainer wants a stable, committed InteractionCommandsExamples.zip in examples/
# (downloadable straight from the repo), so the zip name does NOT carry the version,
# unlike a content pack's own build.ps1.
$zipPath = Join-Path $examplesDir "$PackName.zip"

Get-ChildItem -Path $examplesDir -Filter "$PackName*.zip" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue
try { Add-Type -AssemblyName 'System.IO.Compression.FileSystem' -ErrorAction Stop } catch { }
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, 'Create')
try {
    $files = Get-ChildItem -Path $pack -Recurse -File -Force
    $createdDirs = @{}
    foreach ($f in $files) {
        $rel = $f.FullName.Substring($pack.Length + 1).Replace('\', '/')
        $parts = $rel -split '/'
        for ($i = 1; $i -lt $parts.Length; $i++) {
            $dir = ($parts[0..($i - 1)] -join '/') + '/'
            if (-not $createdDirs.ContainsKey($dir)) {
                $zip.CreateEntry($dir, [System.IO.Compression.CompressionLevel]::NoCompression).Open().Close()
                $createdDirs[$dir] = $true
            }
        }
        $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
        $stream = $entry.Open()
        $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
    }
    Write-Host "Built $zipPath ($($files.Count) files, $($createdDirs.Count) dir entries)"
} finally {
    $zip.Dispose()
}

if ($Install) {
    if (-not $ModsDir) {
        Write-Host "No Mods folder set - pass -ModsDir <path> or set `$env:HYTALE_MODS_DIR to auto-install. Built zip only."
    } elseif (-not (Test-Path $ModsDir)) {
        Write-Warning "Mods folder '$ModsDir' not found. Built zip only."
    } else {
        Get-ChildItem -Path $ModsDir -Filter "$PackName*.zip" -File -ErrorAction SilentlyContinue |
            Remove-Item -Force -ErrorAction SilentlyContinue
        $dest = Join-Path $ModsDir "$PackName.zip"
        Copy-Item $zipPath $dest -Force
        Write-Host "Installed to $dest"
    }
}
