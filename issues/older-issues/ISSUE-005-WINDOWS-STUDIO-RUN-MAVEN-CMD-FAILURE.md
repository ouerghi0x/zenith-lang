# ISSUE-005: Zenith Studio Windows Subprocess Spawn Failure (`Process terminated with exit code 1`) on Run Button Execution

| Metadata Field | Value |
| :--- | :--- |
| **Issue ID** | `ISSUE-005` |
| **Date Discovered** | `2026-11-08` |
| **Date Fixed** | `2026-11-08` |
| **Status** | `FIXED` |
| **Tested** | `YES (Verified via live studio run test with mvn.cmd streaming)` |
| **Severity** | `P0 (Blocker — Unable to run projects from Studio on Windows)` |
| **Subsystem** | `studio`, `runner`, `windows-compatibility` |
| **Affected Components** | `ZenithStudioServer.java` (`RunHandler`, `ProcessBuilder`) |

---

## 1. Problem Description & Root Cause

### Symptoms
When clicking the **"Run"** button in **Zenith Studio** (both Community and PRO editions on Windows):
1. The console displays:
   ```text
   [Zenith Studio] Generating project files into target/studio-run...
   [Zenith Studio] Notice: PostgreSQL not detected on localhost:5432. Auto-switching to embedded H2 in-memory database for instant local execution!
   [Zenith Studio] Using Maven executable: mvn.cmd
   [Zenith Studio] Process terminated with exit code 1
   [Zenith Studio] Tip: Ensure 'mvn' (Apache Maven) is installed and in your PATH, or run 'mvn spring-boot:run' inside 'target/studio-run'.
   ```
2. No Spring Boot banner or logs are produced, and the process terminates immediately with `exit code 1`.

### Root Cause
In `ZenithStudioServer.java` line 485:
```java
// BEFORE (Buggy command formulation on Windows):
pb = new ProcessBuilder("cmd.exe", "/c", "\"" + mavenCmd + "\" spring-boot:run");
```
When `ProcessBuilder` executes on Windows, wrapping `\"" + mavenCmd + "\""` causes Windows `cmd.exe /c` quote-stripping rules to parse the executable incorrectly (treating quotes as part of the command token or misinterpreting the delimiter when executing batch scripts like `mvn.cmd`). 
As a result, `cmd.exe` fails to dispatch the executable or returns exit code 1 immediately.

---

## 2. Solution & Fix

### Code Fix in `ZenithStudioServer.java`
Changed line 485 to avoid synthetic nested quoting when invoking `cmd.exe /c`:
```java
// AFTER (Fixed Windows invocation):
pb = new ProcessBuilder("cmd.exe", "/c", mavenCmd + " spring-boot:run");
```
`ProcessBuilder` properly preserves the command string and `cmd.exe` directly launches `mvn.cmd spring-boot:run` in `target/studio-run`.

---

## 3. Verification & Results

1. **Standalone Java Verification**:
   - Tested execution of `mvn.cmd spring-boot:run` in `target/studio-run` via `ProcessBuilder("cmd.exe", "/c", mavenCmd + " spring-boot:run")`.
   - Confirmed Maven scans projects, resolves dependencies, compiles classes, and boots Spring Boot.
2. **End-to-End Studio API Verification**:
   - Triggered `POST /api/run` against `ZenithStudioServer` running `schema.zen` in `zenith-pro`.
   - Confirmed `/api/logs` streams:
     ```text
     [INFO] Scanning for projects...
     [INFO] Building ShopSphere 1.0.0
     [INFO] >>> spring-boot:3.2.5:run (default-cli) > test-compile @ shop-sphere >>>
     [INFO] Compiling 41 source files with javac [debug release 17] to target\classes
     ...
     :: Spring Boot ::                (v3.2.5)
     ```
3. **Binaries Updated**:
   - Recompiled and repackaged `bin/zenith.jar` via `build-cli.bat`.
   - Rebuilt `zenith-pro.jar` via `build-pro.bat`.
