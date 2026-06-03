<#
.SYNOPSIS
  Capture a screenshot from a connected Android emulator or device for the
  Play Store listing.

.DESCRIPTION
  Uses adb to grab the current screen and saves it to
  play-store-assets/screenshots/. Captures to the device first and pulls the
  file (rather than piping binary over stdout) so the PNG is never corrupted
  by PowerShell's text redirection.

.EXAMPLE
  .\scripts\take-screenshot.ps1
  .\scripts\take-screenshot.ps1 -Name main-grid
#>
param(
    # Optional base name for the file (a number is appended if it already exists).
    [string]$Name = "screenshot"
)

$ErrorActionPreference = "Stop"

# Locate adb: prefer ANDROID_HOME / ANDROID_SDK_ROOT, fall back to PATH.
$adb = $null
foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
    if ($root) {
        $candidate = Join-Path $root "platform-tools\adb.exe"
        if (Test-Path $candidate) { $adb = $candidate; break }
    }
}
if (-not $adb) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $adb = $cmd.Source }
}
if (-not $adb) {
    throw "adb not found. Set ANDROID_HOME or add platform-tools to PATH."
}

# Require exactly one connected device/emulator.
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
if ($devices.Count -eq 0) {
    throw "No emulator or device connected. Start one, then re-run."
}
if ($devices.Count -gt 1) {
    throw "Multiple devices connected. Disconnect extras or set `$env:ANDROID_SERIAL."
}

# Output directory next to the other Play assets.
$repoRoot = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $repoRoot "play-store-assets\screenshots"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

# Pick a non-colliding filename.
$index = 1
do {
    $outFile = Join-Path $outDir ("{0}-{1:00}.png" -f $Name, $index)
    $index++
} while (Test-Path $outFile)

# Capture on device, pull, clean up.
$remote = "/sdcard/_ps_screenshot.png"
& $adb shell screencap -p $remote
& $adb pull $remote $outFile | Out-Null
& $adb shell rm $remote

Write-Host "Saved $outFile"
