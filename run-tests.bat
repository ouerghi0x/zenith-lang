@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Zenith Feature #1 E2E Test Suite Runner
echo ===================================================

:: 1. Ensure core CLI is compiled and bin\zenith.jar is up to date
call build-cli.bat
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compiler build failed. Aborting test execution.
    exit /b %ERRORLEVEL%
)

:: 2. Ensure target\test-classes directory exists
if not exist "target\test-classes" mkdir target\test-classes

:: 3. Collect all Java test sources
dir /s /b src\test\java\*.java > test-sources.txt 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] No test sources found in src\test\java.
    exit /b 1
)

:: 4. Compile test sources
echo [INFO] Compiling E2E test sources...
javac -encoding UTF-8 -cp "bin\zenith.jar" -d target\test-classes @test-sources.txt
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Test compilation failed. Please fix test errors and retry.
    exit /b %ERRORLEVEL%
)

:: 5. Execute E2E Test Runner
echo [INFO] Executing E2E test suite...
java -cp "target\test-classes;bin\zenith.jar" dev.zenith.test.E2ETestSuiteRunner %*
exit /b %ERRORLEVEL%
