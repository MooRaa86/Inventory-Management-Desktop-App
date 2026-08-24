# Builds the complete portable application into dist\InventoryManager\
# Layout produced:
#   InventoryManager\
#     start.cmd                 <- double-click to run
#     app\main.js, package.json (electron shell)
#     electron\                 <- prebuilt Electron binaries
#     backend\inventory-backend.jar (+ data/, backups/, config/, logs/ created on first run)
#     runtime\                  <- jlink-trimmed JRE (no JDK needed on target machine)
#
# Prereqs on the build machine only: Node+npm, Maven, JDK (JAVA_HOME), frontend deps installed.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$out  = Join-Path $root "dist\InventoryManager"

Write-Host "== 1. Frontend build =="
Push-Location "$root\frontend"
npm run build
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "frontend build failed" }
Pop-Location

Write-Host "== 2. Copy SPA into backend static resources =="
New-Item -ItemType Directory -Force -Path "$root\backend\src\main\resources\static" | Out-Null
Copy-Item "$root\frontend\dist\*" "$root\backend\src\main\resources\static\" -Recurse -Force

Write-Host "== 3. Backend package =="
Push-Location "$root\backend"
mvn -q -B -DskipTests package
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "backend build failed" }
Pop-Location

Write-Host "== 4. Assemble output folder =="
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$out\backend", "$out\app" | Out-Null
Copy-Item "$root\backend\target\inventory-backend.jar" "$out\backend\" -Force
Copy-Item "$root\desktop\main.js", "$root\desktop\package.json" "$out\app\" -Force
# Electron runtime (prebuilt binaries already downloaded by npm install)
$electronDist = "$root\desktop\node_modules\electron\dist"
if (-not (Test-Path $electronDist)) { throw "electron not installed in desktop/ - run: cd desktop && npm install" }
Copy-Item $electronDist "$out\electron" -Recurse -Force

@'
@echo off
title Inventory Manager
start "" "%~dp0electron\electron.exe" "%~dp0app"
'@ | Set-Content "$out\start.cmd" -Encoding Ascii

Write-Host "== 5. jlink runtime =="
$jlinkBin = Join-Path $env:JAVA_HOME "bin"
& "$jlinkBin\jlink.exe" `
    --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.zipfs `
    --strip-debug --no-header-files --no-man-pages --compress zip-6 `
    --output "$out\runtime"
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

$sizeMb = [math]::Round((Get-ChildItem $out -Recurse | Measure-Object Length -Sum).Sum / 1MB)
Write-Host ""
Write-Host "PORTABLE PACKAGE READY: $out ($sizeMb MB)"
