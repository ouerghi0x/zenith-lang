@echo off
setlocal

echo ========================================================
echo   Zenith Studio PRO - Desktop Packaging
echo ========================================================

REM Step 1: Check if Node.js is installed
where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Node.js is required to package the desktop app.
    pause
    exit /b 1
)

REM Step 2: Ensure bin\zenith.jar is built
if not exist "..\bin\zenith.jar" (
    echo [1/3] Building Zenith CLI Jar...
    cd ..
    call build-cli.bat
    cd desktop
)

echo [2/3] Checking dependencies...
if not exist "node_modules" (
    echo Installing packaging dependencies...
    npm install
)

echo [3/3] Packaging native Windows installer (Zenith-Studio-Setup.exe)...
call npm run build:installer

echo.
echo ========================================================
echo   BUILD COMPLETE!
echo   Installer created at: desktop\dist\Zenith-Studio-Setup.exe
echo ========================================================

