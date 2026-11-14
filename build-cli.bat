@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Zenith CLI Build and Update
echo ===================================================

:: 1. Collect all Java source files
dir /s /b src\main\java\*.java > sources.txt
echo [1/3] Found source files in src\main\java

:: 2. Ensure target\classes directory exists
if not exist "target\classes" mkdir target\classes

:: 3. Compile sources
echo [2/3] Compiling sources and syncing resources...
javac -encoding UTF-8 -cp "bin\zenith.jar" -d target\classes @sources.txt
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed. Please fix syntax errors and retry.
    exit /b %ERRORLEVEL%
)

:: Copy studio & application resources into target\classes
if exist "src\main\resources" (
    xcopy /s /e /y /q "src\main\resources\*" "target\classes\" >nul 2>&1
)

:: 4. Update the existing executable jar. Preserve its manifest and report locked-file errors.
echo [3/3] Updating bin\zenith.jar...
jar uf bin\zenith.jar -C target\classes .
if errorlevel 1 (
    echo [ERROR] Failed to update bin\zenith.jar.
    echo Close any running Zenith Studio or CLI process that is using the jar, then run this build again.
    exit /b 1
)

echo.
echo [SUCCESS] bin\zenith.jar has been updated successfully!
echo You can now test with: bin\zenith.bat validate schema.zen
