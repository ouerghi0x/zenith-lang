# Zenith Issues & Quality Trackers

This folder contains formal root cause analyses, reproductions, and fix records for issues discovered in the Zenith ecosystem.

---

## Issue Registry

| ID | Title | Severity | Status | Subsystems | Target Milestone |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **[ISSUE-001](./ISSUE-001-HARDCODED-JWT-SECRET.md)** | Hardcoded Fallback JWT Secret in Production (`SecurityConfig.java` / `JwtTokenProvider.java`) | P0 (Critical Security) | **FIXED** | `security`, `generator` | `v1.0.1` |
| **[ISSUE-002](./ISSUE-002-ENTITY-RETURN-IN-REST-ENDPOINT.md)** | Database Entities Exposed Directly in Controller Endpoints (Bypassing DTO Layer) | P1 (High Architectural) | **FIXED** | `controller`, `generator`, `dto` | `v1.0.1` |
| **[ISSUE-003](./ISSUE-003-distribution.md)** | Self-Contained Distribution Packaging: Portable JRE, Embedded Maven & Standalone Native Artifacts | P1 (Core Experience) | **FIXED** | `distribution`, `cli`, `packaging` | `v1.0.0-GA` |
| **[ISSUE-004](./ISSUE-004-INCOMPATIBLE-FIELD-TYPE-SCHEMA-MUTATION.md)** | Incompatible Model Field Type Mutation (e.g. `DateTime` to `String`) Causes Hibernate Schema Validation Crash on Existing DB | P1 (High Availability) | **FIXED** | `studio`, `generator`, `analyzer`, `flyway` | `v1.0.1` |
| **[ISSUE-005](./ISSUE-005-WINDOWS-STUDIO-RUN-MAVEN-CMD-FAILURE.md)** | Zenith Studio Windows Subprocess Spawn Failure (`Process terminated with exit code 1`) on Run Button Execution | P0 (Blocker) | **FIXED** | `studio`, `runner`, `windows-compatibility` | `v1.0.1` |
| **[ISSUE-006](./ISSUE-006-MODEL-INDEX-BEFORE-RELATIONS-GREEDY-FIELD-PARSE.md)** | Model-Level `@index([...])` Placed Above `relations` Block Greedily Consumed by Preceding Field's Annotation Loop | P1 (Parser Grammar) | **FIXED** | `parser`, `ast`, `indexes` | `v1.0.2` |
| **[ISSUE-007](./ISSUE-007-UNSUPPORTED-FIELD-ANNOTATION-SILENTLY-IGNORED.md)** | Unrecognized or Misspelled Field Annotations (`@uniquesz`, `@autoNows`) Silently Ignored Without Compiler Error | P1 (Compiler Diagnostics) | **FIXED** | `analyzer`, `parser`, `ast` | `v1.0.2` |
| **[ISSUE-008](./ISSUE-008-MONACO-RED-ERROR-LINE-HIGHLIGHT-MISSING.md)** | Red Error Line Highlighting & Squiggly Marker Not Rendered in Studio Monaco Editor Due to `baseDir` Filename Mismatch | P1 (Studio Diagnostics UX) | **FIXED** | `studio`, `resolver`, `monaco` | `v1.0.2` |
| **[ISSUE-009](./ISSUE-009-ERGONOMIC-JWT-SECRET-AND-DURATION-CONFIG.md)** | Ergonomic JWT Config (Option 2): Auto-Derived 256-Bit Secret, Short Passphrase Stretching & Duration Units (`24h`, `30m`, `7d`) | P1 (Developer Experience) | **FIXED** | `config`, `parser`, `analyzer`, `security` | `v1.0.2` |
| **[ISSUE-010](./ISSUE-010-UNIFIED-API-ROLE-ACCESS-CONTROL.md)** | Unified API Role Access Control: Single Canonical `roles` Syntax Across CRUD & Custom Endpoints | P2 (Ergonomics / RFC) | **OPEN** | `parser`, `ast`, `analyzer`, `security` | `v1.1.0` |

---

## Issue Lifecycle Workflow

```
[DISCOVERED / REPRODUCED] 
       │
       ▼
[ROOT CAUSE ANALYSIS (RCA)] 
       │
       ▼
[CODE MODIFICATION & REGRESSION TESTS] 
       │
       ▼
[DOCUMENTATION IN PRIVATE/ISSUES] 
       │
       ▼
[MARKED AS FIXED & VERIFIED]
```
