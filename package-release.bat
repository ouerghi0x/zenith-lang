@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Zenith Multi-Platform Distribution Packager
echo ===================================================

set VERSION=1.0.0
set DIST_DIR=dist\v%VERSION%

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

echo [1/4] Ensuring CLI binary is built...
call build-cli.bat
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed. Aborting packaging.
    exit /b %ERRORLEVEL%
)

echo [2/4] Packaging Standalone JAR...
copy /y "bin\zenith.jar" "%DIST_DIR%\zenith-%VERSION%.jar" >nul

echo [3/4] Creating Windows Archive (ZIP)...
powershell -NoProfile -Command ^
    "$temp = Join-Path $env:TEMP ('zenith-win-' + [guid]::NewGuid().ToString());" ^
    "New-Item -ItemType Directory -Path $temp | Out-Null;" ^
    "Copy-Item 'bin\zenith.jar' $temp;" ^
    "Copy-Item 'bin\zenith.bat' $temp;" ^
    "Copy-Item 'bin\zenith.ps1' $temp;" ^
    "Copy-Item 'LICENSE' $temp;" ^
    "Compress-Archive -Path (Join-Path $temp '*') -DestinationPath '%DIST_DIR%\zenith-v%VERSION%-windows-x64.zip' -Force;" ^
    "Remove-Item -Recurse -Force $temp;"

echo [4/4] Creating Unix / macOS Archive (TAR.GZ)...
powershell -NoProfile -Command ^
    "$temp = Join-Path $env:TEMP ('zenith-unix-' + [guid]::NewGuid().ToString());" ^
    "New-Item -ItemType Directory -Path $temp | Out-Null;" ^
    "Copy-Item 'bin\zenith.jar' $temp;" ^
    "Copy-Item 'bin\zenith' $temp;" ^
    "Copy-Item 'LICENSE' $temp;" ^
    "tar -czf '%DIST_DIR%\zenith-v%VERSION%-linux-macos.tar.gz' -C $temp .;" ^
    "Remove-Item -Recurse -Force $temp;"

echo.
echo Computing SHA-256 Checksums...
python tools\gen-checksums.py

echo.
echo [SUCCESS] Distribution artifacts created in %DIST_DIR%:
dir /b "%DIST_DIR%"
