<#
.SYNOPSIS
  Capture the Android Auto Desktop Head Unit (DHU) window to a PNG for the
  Play Store car-app screenshots.

.DESCRIPTION
  The DHU renders the car display in a desktop window, so adb screencap cannot
  see it. This finds the desktop-head-unit process window, brings it to the
  foreground, and captures its client area (the car screen itself, without the
  title bar) into play-store-assets/screenshots/.

.EXAMPLE
  .\scripts\capture-car-screenshot.ps1 -Name car-grid
#>
param(
    [string]$Name = "car"
)

$ErrorActionPreference = "Stop"

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32 {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    public struct RECT { public int Left, Top, Right, Bottom; }
    public struct POINT { public int X, Y; }
}
"@

# Match the screen-capture pixel space to the Win32 coordinate calls; without this,
# on a scaled display (e.g. 125%) the client origin and the grab are offset.
[Win32]::SetProcessDPIAware() | Out-Null

$proc = Get-Process desktop-head-unit -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $proc) {
    throw "DHU window not found. Launch it first with scripts\launch-head-unit.ps1 and open the app in it."
}

$hwnd = $proc.MainWindowHandle
[Win32]::ShowWindow($hwnd, 9) | Out-Null   # SW_RESTORE
[Win32]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep -Milliseconds 400

$rect = New-Object Win32+RECT
[Win32]::GetClientRect($hwnd, [ref]$rect) | Out-Null
$topLeft = New-Object Win32+POINT
$topLeft.X = 0; $topLeft.Y = 0
[Win32]::ClientToScreen($hwnd, [ref]$topLeft) | Out-Null

$width  = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top
if ($width -le 0 -or $height -le 0) { throw "Could not read DHU window size." }

Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap $width, $height
$gfx = [System.Drawing.Graphics]::FromImage($bmp)
$gfx.CopyFromScreen($topLeft.X, $topLeft.Y, 0, 0, $bmp.Size)

$repoRoot = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $repoRoot "play-store-assets\screenshots"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

$index = 1
do {
    $outFile = Join-Path $outDir ("{0}-{1:00}.png" -f $Name, $index)
    $index++
} while (Test-Path $outFile)

$bmp.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png)
$gfx.Dispose(); $bmp.Dispose()
Write-Host "Saved $outFile ($width x $height)"
