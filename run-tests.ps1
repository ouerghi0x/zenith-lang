# Zenith Feature #1 E2E Test Suite Runner (PowerShell)
param(
    [string]$Tier = ""
)

$ErrorActionPreference = "Stop"

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  Zenith Feature #1 E2E Test Suite Runner" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

# 1. Build CLI
& cmd /c build-cli.bat
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compiler build failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# 2. Ensure target\test-classes exists
if (-not (Test-Path "target\test-classes")) {
    New-Item -ItemType Directory -Path "target\test-classes" | Out-Null
}

# 3. Find test sources
$testFiles = Get-ChildItem -Recurse -Path "src\test\java" -Filter "*.java" | Select-Object -ExpandProperty FullName
if ($testFiles.Count -eq 0) {
    Write-Error "No test files found in src\test\java"
    exit 1
}
$testFiles | Set-Content -Path "test-sources.txt" -Encoding UTF8

# 4. Compile test sources
Write-Host "[INFO] Compiling E2E test sources..." -ForegroundColor Yellow
& javac -encoding UTF-8 -cp "bin\zenith.jar" -d "target\test-classes" "@test-sources.txt"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Test compilation failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# 5. Execute runner
Write-Host "[INFO] Executing E2E test suite..." -ForegroundColor Yellow
$runnerArgs = @("-cp", "target\test-classes;bin\zenith.jar", "dev.zenith.test.E2ETestSuiteRunner")
if ($Tier -ne "") {
    $runnerArgs += @("--tier", $Tier)
}

& java $runnerArgs
exit $LASTEXITCODE
