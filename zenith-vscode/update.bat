@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Zenith VS Code Extension - Package and Update
echo ===================================================

cd /d "%~dp0"

echo [1/2] Packaging extension (.vsix)...
call vsce package --allow-missing-repository
if %ERRORLEVEL% NEQ 0 (
    echo [INFO] Trying via npx @vscode/vsce...
    call npx -y @vscode/vsce package --allow-missing-repository
)

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to package extension.
    pause
    exit /b %ERRORLEVEL%
)

:: Find the most recent .vsix file
for /f "delims=" %%F in ('dir /b /o:-d *.vsix 2^>nul') do (
    set "VSIX_FILE=%%F"
    goto :found_vsix
)

:found_vsix
if "%VSIX_FILE%"=="" (
    echo [ERROR] No .vsix package found.
    pause
    exit /b 1
)

echo.
echo [2/2] Installing %VSIX_FILE% into VS Code...
call code --install-extension "%VSIX_FILE%" --force
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to install extension into VS Code.
    echo Ensure "code" is accessible in your system PATH.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo [SUCCESS] Extension %VSIX_FILE% successfully installed!
echo To apply updates in VS Code:
echo   Press Ctrl+Shift+P -^> "Developer: Reload Window"
echo ===================================================
echo.

