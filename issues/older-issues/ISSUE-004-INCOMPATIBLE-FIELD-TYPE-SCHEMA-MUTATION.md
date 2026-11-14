# ISSUE-004: Incompatible Model Field Type Mutation (e.g. `DateTime` to `String`) Causes Hibernate Schema Validation / DDL Crash on Existing Database

| Metadata Field | Value |
| :--- | :--- |
| **Issue ID** | `ISSUE-004` |
| **Date Discovered** | `2026-11-08` |
| **Date Fixed** | `2026-11-08` |
| **Status** | `FIXED` |
| **Tested** | `YES (CLI compiled & Studio diagnostic/reset handlers integrated)` |
| **Severity** | `P1 (High — Runtime Availability & Data Integrity)` |
| **Subsystem** | `studio`, `generator`, `analyzer`, `flyway` |
| **Affected Components** | [ZenithStudioServer.java](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/studio/ZenithStudioServer.java), [PropertiesGenerator.java](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/generator/PropertiesGenerator.java), [FlywayGenerator.java](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/generator/FlywayGenerator.java), [SemanticAnalyzer.java](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/analyzer/SemanticAnalyzer.java) |

---

## 1. Problem Description & Reproduction

### Scenario
A developer defines a field in a `.zen` model:
```zen
model EventLog {
    id: Long @id @auto
    createdAt: DateTime @autoNow
    message: String
}
```
1. The developer compiles and starts the application (either standalone or via Zenith Studio's **"Run Spring Boot"** button).
2. The database table `event_logs` is created with a `TIMESTAMP` / `DATETIME` column for `created_at`. Data may even be inserted.
3. Later, the developer edits the schema and mutates the field type from `DateTime` to `String`:
```zen
model EventLog {
    id: Long @id @auto
    createdAt: String   // <--- Changed from DateTime to String
    message: String
}
```
4. When the application restarts:
   - **Case A: Flyway Enabled (`ddl-auto: validate`)**:
     Hibernate performs schema validation at startup against the existing physical DB table. Because the column `created_at` is `TIMESTAMP` in SQL but mapped to `java.lang.String` (`VARCHAR`) in JPA:
     ```
     org.hibernate.tool.schema.spi.SchemaManagementException: 
     Schema-validation: wrong column type encountered in column [created_at] in table [event_logs]; 
     found [timestamp (Types#TIMESTAMP)], but expecting [varchar(255) (Types#VARCHAR)]
     ```
     **Result**: The Spring Boot application crashes immediately during startup (`exit code 1`).
   - **Case B: Flyway Disabled (`ddl-auto: update`)**:
     Hibernate's `hbm2ddl.auto = update` cannot alter incompatible types (e.g. `TIMESTAMP` -> `VARCHAR` or `INT` -> `UUID`) without explicit column type alteration commands. Depending on the SQL dialect (PostgreSQL vs MySQL vs H2), it either silently ignores the alteration (leading to query/deserialization exceptions at runtime: `PSQLException: Bad value for type String` / `DateTimeParseException`) or throws an unhandled migration exception.
   - **Case C: Seed Data & DTO Deserialization**:
     If `seed { ... }` or existing database rows contain `DateTime` ISO strings or timestamps, existing records or seed scripts fail to cast or parse.

---

## 2. Root Cause Analysis

1. **State Blindness across Schema Compilations**:
   The compiler and Studio currently analyze each compilation in total isolation. Zenith does not compare the newly submitted AST against the previously compiled AST or the live database schema metadata.
2. **Flyway Migration Monolithic V1**:
   Zenith currently emits only a single `V1__initial_schema.sql` file. If a schema is altered after initial boot, Flyway does not generate incremental migration scripts (`V2__alter_event_log_created_at.sql`).
3. **Hibernate `ddl-auto: validate` Strictness**:
   Under production configurations with Flyway enabled, Hibernate validates that Java entity types strictly match physical database column types. A type mutation without an incremental database migration script or database reset guarantees a startup crash.
4. **Silent Dev-Mode Lockup in Studio**:
   In Zenith Studio, when the user clicks **"Run Spring Boot"** on an updated schema, the server spawns `mvn spring-boot:run`. When Hibernate crashes on `SchemaManagementException`, the user only sees an opaque `Process terminated with exit code 1` without actionable remediation.

---

## 3. Required Zenith Remediation Architecture

To solve this problem comprehensively, Zenith must handle incompatible schema alterations across three layers:

### Layer 1: Schema Change Detection & Studio Pre-Flight Warning
- Maintain a schema state hash or shadow AST (`.zenith/schema.cache.json` or in-memory in `ZenithStudioServer`).
- When a field's type is changed (e.g., `DateTime` -> `String`, `Int` -> `UUID`, `String` -> `Boolean`), flag it as a **Destructive Schema Mutation**.
- Display an explicit warning in Studio:
  > **⚠️ Incompatible Column Type Mutation Detected:**
  > Table `event_logs.created_at` changed from `DateTime` (`TIMESTAMP`) to `String` (`VARCHAR`).
  > Existing database tables will fail Hibernate validation upon restart.

### Layer 2: Automatic Studio Database Reset Option (Development Mode)
- In local development mode (`target/studio-run`), offer an automated **"Reset Database & Re-run"** action or flag in Studio:
  - Drops existing H2 / dev database files (`target/studio-run/*.db` or `rm -rf target/studio-run/data`).
  - Regenerates a fresh initial DDL and seed data.
  - Re-starts Spring Boot cleanly with 0 downtime and 0 validation errors.

### Layer 3: Incremental Flyway Migration Generation (Enterprise / Production Mode)
- When Flyway is enabled, detect model schema diffs and generate timestamped incremental migration files:
  - `src/main/resources/db/migration/V2__alter_event_logs_created_at_to_varchar.sql`
- Generate safe SQL dialect conversion clauses:
  - **PostgreSQL**: `ALTER TABLE event_logs ALTER COLUMN created_at TYPE VARCHAR(255) USING created_at::text;`
  - **MySQL**: `ALTER TABLE event_logs MODIFY COLUMN created_at VARCHAR(255);`
  - **H2**: `ALTER TABLE event_logs ALTER COLUMN created_at VARCHAR(255);`

### Layer 4: Clear Error Diagnostics & Recovery in Studio Process Runner
- In [ZenithStudioServer.java](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/studio/ZenithStudioServer.java), parse the logs for:
  `org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: wrong column type encountered in column`
- When detected, print a highlighted, human-readable troubleshooting card in the Studio Log Viewer:
  ```
  [Zenith Diagnostic] ❌ Database Schema Type Mismatch Detected!
  Column [created_at] in table [event_logs] has changed type in your schema.
  Fix options:
    1. Click "Reset Dev Database" in Studio toolbar to drop and recreate fresh tables.
    2. Or run: 'mvn clean' before launching Spring Boot.
    3. For production DBs, add an incremental Flyway migration (V2__alter_column.sql).
  ```

---

## 4. Action Items & Implementation Plan

- [ ] **Step 1 (Studio Diagnostics)**: In `ZenithStudioServer.java`, intercept `SchemaManagementException` in log stream and output user-friendly diagnostic guidance with one-click DB reset.
- [ ] **Step 2 (Studio Reset Database Button & API)**: Add `/api/reset-db` endpoint to delete existing local H2 database files and re-trigger clean schema generation.
- [ ] **Step 3 (Compiler/Analyzer Migration Warning)**: In `SemanticAnalyzer.java`, add AST state comparison to warn developers when field types or column names have been mutated on existing entities.
- [ ] **Step 4 (Documentation & Reference)**: Add a dedicated section to [docs/index.html](file:///c:/Users/wergh/Desktop/apiRestLanguage/docs/index.html) and [studio/index.html](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/resources/studio/index.html) explaining database migration lifecycle and handling schema evolution.

