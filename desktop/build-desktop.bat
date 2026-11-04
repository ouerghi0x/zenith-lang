@echo off
setlocal enabledelayedexpansion

REM Fix working directory to script location
set "DESKTOP_DIR=%~dp0"
set "ROOT_DIR=%~dp0.."

echo ========================================================
echo   Zenith Studio PRO - Desktop Packaging
echo ========================================================

REM Step 1: Check Node.js
where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Node.js is required to package the desktop app.
    pause
    exit /b 1
)

REM Step 2: Ensure bin\zenith.jar is built in project root
if not exist "%ROOT_DIR%\bin\zenith.jar" (
    echo [1/3] Building Zenith CLI Jar...
    pushd "%ROOT_DIR%"
    call build-cli.bat
    popd
)

REM Step 3: Switch to the desktop directory
cd /d "%DESKTOP_DIR%"

echo [2/3] Checking dependencies...
if not exist "node_modules" (
    echo Installing packaging dependencies in %DESKTOP_DIR%...
    call npm install
)

echo [3/3] Packaging native Windows installer (Zenith-Studio-Setup.exe)...
call npm run build:installer

echo.
echo ========================================================
echo   BUILD COMPLETE!
echo   Installer created at: %DESKTOP_DIR%dist\Zenith-Studio-Setup.exe
echo ========================================================
