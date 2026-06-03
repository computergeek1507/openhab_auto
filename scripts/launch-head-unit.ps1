<#
.SYNOPSIS
  Launch the Android Auto Desktop Head Unit (DHU) against a connected device,
  so the app's car UI can be exercised and screenshotted.

.DESCRIPTION
  Sets up the adb port forward the DHU needs (tcp:5277) and starts the head
  unit. Requires that the connected phone/emulator already has Android Auto
  installed with developer mode enabled and the head-unit server started
  (see PLAY_PUBLISHING.md for the one-time device setup).

  Screenshot the DHU window itself (Alt+PrintScreen, or the Snipping Tool) —
  the car display is rendered on the desktop, not on the phone screen, so
  adb screencap will NOT capture it.
#>

$ErrorActionPreference = "Stop"

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw "ANDROID_HOME / ANDROID_SDK_ROOT not set." }

$adb = Join-Path $sdk "platform-tools\adb.exe"
$dhu = Join-Path $sdk "extras\google\auto\desktop-head-unit.exe"
foreach ($p in @($adb, $dhu)) {
    if (-not (Test-Path $p)) { throw "Not found: $p" }
}

# DHU connects to the head-unit server on the device over this forwarded port.
& $adb forward tcp:5277 tcp:5277 | Out-Null
Write-Host "Forwarded tcp:5277. Starting Desktop Head Unit..."

# Run from its own directory so it finds libusb / config alongside it.
Push-Location (Split-Path $dhu -Parent)
try {
    & $dhu
}
finally {
    Pop-Location
    & $adb forward --remove tcp:5277 2>$null
}
