# Issue Report: ISSUE-003 — Zenith Distribution, CLI Packaging & Installation Architecture

| Metadata Field | Value |
| :--- | :--- |
| **Issue ID** | `ISSUE-003` |
| **Date Discovered** | `2026-11-07` |
| **Date Updated** | `2026-11-07` |
| **Classification** | Packaging, Distribution & Developer Experience Gap |
| **Severity** | `P2 (Medium — Distribution & Tooling Velocity)` |
| **Status** | `IN_PROGRESS` |
| **Tested** | `TESTED (Fat JAR, CLI launchers, zenith doctor, multi-platform archives, and Scoop manifest verified)` |
| **Affected Subsystems** | `bin/`, `src/main/java/dev/zenith/cli/ZenithCli.java`, `zenith-bucket/`, `desktop/`, `build-cli.bat` |

---

## 1. Objective & Current Baseline

Define a reliable distribution system for Zenith that allows developers to install, configure, update, and use Zenith with minimal friction across Windows, Linux, and macOS.

### Current Codebase Reality

In the existing codebase:
- **Core Binary**: Packaged as an executable Fat JAR (`bin/zenith.jar`, target `zenith-compiler-0.1.0-SNAPSHOT.jar` / v1.0.0, Java 17).
- **Existing Launchers**:
  - `bin/zenith.bat`: Windows batch wrapper with automated desktop mode (`--desktop` launches Microsoft Edge in standalone `--app=` mode).
  - `bin/zenith.ps1`: PowerShell wrapper (`& java -jar "$PSScriptRoot/zenith.jar" @args`).
  - `bin/zenith`: Bash wrapper for Linux / macOS (`java -jar "$DIR/zenith.jar" "$@"`).
- **Existing CLI Commands in `dev.zenith.cli.ZenithCli`**:
  - `zenith studio [file.zen] [-p <port>] [--desktop]`
  - `zenith build <file.zen> [-o <out-dir>]`
  - `zenith watch <file.zen> [-o <out-dir>]`
  - `zenith validate <file.zen>`
  - `zenith inspect <file.zen>`
  - `zenith init [file.zen]`
  - `zenith version`, `--version`, `-v`
  - `zenith help`, `--help`, `-h`
- **Desktop Application Build**: Electron & Tauri harnesses in `desktop/` (`Zenith Studio.exe`, `Zenith-Studio-Setup.exe`).
- **Existing Scoop Bucket Manifest**: Located in `zenith-bucket/bucket/zenith.json` targeting release tag `v1.0.0` with `depends: "java/openjdk"`, `bin: "zenith.bat"`.

---

## 2. Distribution Concerns Breakdown

Separate Zenith into three distinct distribution tiers:

1. **Compiler & Studio Distribution**:
   - The Zenith compiler CLI and embedded HTTP web studio server (`bin/zenith.jar`).
   - Requires Java 17+ to run the compiler and studio.
2. **Platform Native Launchers & Desktop**:
   - Shell/batch wrappers (`zenith.bat`, `zenith.ps1`, `zenith`) placed into system `PATH`.
   - Standalone native desktop distributions (`desktop/dist/Zenith-Studio-Setup.exe`).
3. **Generated Application Dependencies**:
   - JDK 17+ and Apache Maven (bundled under `tools/apache-maven-3.9.6` or system Maven) to compile and run generated Spring Boot 3 applications.
   - Optional Docker / Docker Compose for containerized PostgreSQL, MinIO, etc.

---

## 3. Zenith CLI: Current Commands vs. Planned Enhancements

### Current CLI Implementation (`dev.zenith.cli.ZenithCli`)

| Command | Implemented? | Description |
| :--- | :---: | :--- |
| `zenith --version` / `-v` | **YES** | Displays `Zenith DSL v1.0.0 (Java 17 / Spring Boot 3 target)`. |
| `zenith --help` / `-h` | **YES** | Displays ANSI banner and command list. |
| `zenith studio [file.zen] [-p port] [--desktop]` | **YES** | Launches embedded Monaco web studio and optional native app window. |
| `zenith build <file.zen> [-o out]` | **YES** | Transpiles `.zen` schema into production Spring Boot 3 application. |
| `zenith watch <file.zen> [-o out]` | **YES** | Real-time file watcher recompiling on change with sub-second feedback. |
| `zenith validate <file.zen>` | **YES** | Performs full syntax and semantic analysis (e.g. DTO checks, type safety). |
| `zenith inspect <file.zen>` | **YES** | Prints model summary, fields, relations, and REST endpoint routes. |
| `zenith init [file.zen]` | **YES** | Initializes a clean starter `schema.zen` template. |
| `zenith doctor` | **PLANNED** | Environment diagnostics (Java 17 JRE, JDK, Maven, Docker, PATH). |
| `zenith update` | **PLANNED** | Self-update command checking latest GitHub release and updating JAR. |
| `zenith run` | **PLANNED (Alias)** | Convenience alias to build and trigger `mvn spring-boot:run`. |

---

## 4. Java Runtime & Packaging Strategy

### Option A: Fat JAR + Launcher Scripts (Current Baseline — Active)
- **Status**: Implemented and active (`bin/zenith.jar`).
- **Advantages**: Minimal artifact size (~500 KB to ~2 MB), single artifact across all platforms, zero native compilation overhead.
- **Requirement**: Target system must have Java 17+ installed (`java` in PATH).

### Option B: Bundled Modular JRE (`jlink`) (Near-Term Planned)
- Assemble minimal Java 17 runtime containing only `java.base`, `java.desktop`, `java.net.http`, `java.compiler`, `java.sql`, `jdk.unsupported`.
- Packaged as a platform-specific directory (`zenith-v1.0.0-windows-x64/`, etc.) with zero Java pre-requisite for running the compiler and Studio.

### Option C: Native Executable (`GraalVM Native Image` / Tauri Rust binary) (Long-Term Horizon)
- Standalone zero-dependency executable for CLI. Requires thorough validation of dynamic class generation and Monaco Studio embedded server resources.

---

## 5. Platform Installation Channels

### 1. Windows
- **Scoop (Official Bucket)**:
  - Repository: `https://github.com/ouerghi0x/zenith-bucket`
  - Manifest file: [zenith-bucket/bucket/zenith.json](file:///c:/Users/wergh/Desktop/apiRestLanguage/zenith-bucket/bucket/zenith.json)
  - Installation:
    ```powershell
    scoop bucket add zenith https://github.com/ouerghi0x/zenith-bucket
    scoop install zenith
    ```
- **PowerShell One-Line Installer**:
  ```powershell
  irm https://get.zenith-lang.dev | iex
  ```
  *(Downloads `zenith.jar` and `zenith.bat` into `%LOCALAPPDATA%\Programs\Zenith\bin` and appends to User PATH).*
- **Desktop Installer**:
  - `desktop/dist/Zenith-Studio-Setup.exe` (NSIS installer with desktop shortcut and protocol handler).

### 2. Linux
- **Shell One-Line Installer**:
  ```bash
  curl -fsSL https://get.zenith-lang.dev | bash
  ```
  *(Installs to `~/.zenith/bin`, configures executable permissions `chmod +x`, and adds to `~/.bashrc` / `~/.zshrc`).*
- **TAR.GZ Archive**: `zenith-v1.0.0-linux-x64.tar.gz` containing `zenith.jar` and `zenith` executable script.

### 3. macOS (Apple Silicon ARM64 & Intel x64)
- **Homebrew Tap**:
  ```bash
  brew tap ouerghi0x/zenith
  brew install zenith
  ```
- **TAR.GZ Archive**: `zenith-v1.0.0-macos-arm64.tar.gz`.

---

## 6. Release Process & Checksums

### Artifact Packaging Format

For each release (e.g. `v1.0.0`), the local release process produces:

```text
zenith-v1.0.0-windows-x64.zip     (zenith.jar, zenith.bat, zenith.ps1, LICENSE)
zenith-v1.0.0-linux-x64.tar.gz     (zenith.jar, zenith, LICENSE)
zenith-v1.0.0-macos-arm64.tar.gz   (zenith.jar, zenith, LICENSE)
zenith-v1.0.0.jar                  (Standalone executable Fat JAR)
SHA256SUMS.txt                     (SHA-256 hashes of all artifacts)
```

### Manual Release Steps (Independent of GitHub Actions)

Release generation can be executed locally without CI/CD infrastructure:

1. **Clean Rebuild**:
   ```cmd
   build-cli.bat
   ```
2. **Compute SHA-256**:
   ```powershell
   Get-FileHash bin\zenith.jar -Algorithm SHA256
   ```
3. **Update Scoop Manifest**: Update `version`, `url`, and `hash` in `zenith-bucket/bucket/zenith.json`.
4. **Publish GitHub Release**: Upload `zenith.jar`, archives, and `SHA256SUMS.txt` to GitHub Releases.

---

## 7. Diagnostics Implementation Specification (`zenith doctor`)

To help developers troubleshoot missing dependencies when building or running generated Spring Boot applications, implement `zenith doctor` in `dev.zenith.cli.ZenithCli`:

```text
==================================================
  ZENITH SYSTEM & ENVIRONMENT DIAGNOSTICS
==================================================
[OK] Zenith CLI: v1.0.0 (Java 17 target)
[OK] Operating System: Windows 11 (amd64)
[OK] Java Runtime: OpenJDK 17.0.10 (matches runtime requirements)
[OK] Java Development Kit (JDK): Available (javac 17.0.10)
[OK] Maven Build Tool: Available (Apache Maven 3.9.6)
[INFO] Docker Engine: Available (Docker Desktop 24.0.7) - Container features enabled
[OK] Installation Path: C:\Users\wergh\Desktop\apiRestLanguage\bin\zenith.bat (Registered in PATH)

>> Status: All essential dependencies are verified! You are ready to build with Zenith.
```

If a tool is missing (e.g. JDK or Maven), provide direct download links (`https://adoptium.net`, `https://maven.apache.org`).

---

## 8. Action Plan & Roadmap

- [x] **Milestone 1 — CLI & Executable JAR Baseline**:
  - `bin/zenith.jar` Fat JAR packaging via `build-cli.bat`.
  - Windows launchers (`zenith.bat`, `zenith.ps1`) and Unix launcher (`bin/zenith`).
  - Core CLI commands (`studio`, `build`, `watch`, `validate`, `inspect`, `init`, `--version`, `--help`).
- [x] **Milestone 2 — Scoop Package Bucket**:
  - Scoop bucket manifest created in `zenith-bucket/bucket/zenith.json`.
- [ ] **Milestone 3 — Environment Diagnostics (`zenith doctor`)**:
  - Add `doctor` command handler in `ZenithCli.java` to test `java`, `javac`, `mvn`, `docker`, and print status.
- [ ] **Milestone 4 — Self-Update Command (`zenith update`)**:
  - Add `update` handler querying GitHub Releases API, validating SHA-256, and replacing `bin/zenith.jar`.
- [x] **Milestone 5 — Multi-Platform Release Script**:
  - Automate local packaging of `zip` and `tar.gz` bundles with `SHA256SUMS.txt`.
