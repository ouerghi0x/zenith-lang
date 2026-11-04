@echo off
setlocal enabledelayedexpansion

REM ===================================================
REM Zenith CLI & Studio Launcher
REM ===================================================

set IS_DESKTOP=0
set STUDIO_PORT=3000
set PREV_ARG=

for %%A in (%*) do (
    if "%%A"=="--desktop" set IS_DESKTOP=1
    if "%%A"=="-d" set IS_DESKTOP=1
    if "!PREV_ARG!"=="-p" set STUDIO_PORT=%%A
    if "!PREV_ARG!"=="--port" set STUDIO_PORT=%%A
    set PREV_ARG=%%A
)

if "%IS_DESKTOP%"=="1" (
    set ZENITH_DESKTOP_LAUNCHED=1
    REM Open Microsoft Edge in pure standalone frameless application mode
    start "" /b cmd /c "timeout /t 1 /nobreak >nul & start msedge --app=http://localhost:%STUDIO_PORT%"
)

java -jar "%~dp0zenith.jar" %*
