# ISSUE-012: Malformed Java Text Block and Escape Syntax in RateLimitingFilter (`RateLimitGenerator.java`)

**Issue ID**: `ISSUE-012`  
**Severity**: **P0 (Critical Compilation Blocker)**  
**Status**: **RESOLVED & VERIFIED**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.generator.RateLimitGenerator`  
**Reporter**: Antigravity Studio Build / Javac Maven Compiler  

---

## 1. Problem Description & Symptoms

When rate limiting is enabled in a schema (e.g. `@rateLimit(requests = 50, per = "1m")` on a custom endpoint or model CRUD), building or running the project in Zenith Studio (`Run` button or `mvn test`) failed with 18 Java compiler errors:

```
[ERROR] /.../RateLimitingFilter.java:[67,30] illegal text block open delimiter sequence, missing line terminator
[ERROR] /.../RateLimitingFilter.java:[68,17] illegal character: '\'
[ERROR] /.../RateLimitingFilter.java:[68,54] unclosed string literal
[ERROR] /.../RateLimitingFilter.java:[68,52] not a statement
[ERROR] /.../RateLimitingFilter.java:[74,17] illegal text block open delimiter sequence, missing line terminator
[INFO] 18 errors
[INFO] BUILD FAILURE
```

---

## 2. Root Cause Analysis

In [`RateLimitGenerator.java`](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/generator/RateLimitGenerator.java) lines 141-150:

```java
sb.append("            String json = \"\"\"{\n");
sb.append("                \\\"timestamp\\\": \\\"\" + Instant.now() + \"\\\",\n");
sb.append("                \\\"status\\\": 429,\n");
...
sb.append("            }\"\"\";\n");
```

This produced invalid Java source syntax in the emitted `RateLimitingFilter.java`:
1. **Invalid Java Text Block Opening**: In Java 15+, a text block `"""` must be immediately followed by a line terminator (`\n`), not `{`. Emitting `"""{` triggers `illegal text block open delimiter sequence, missing line terminator`.
2. **Escaped Backslashes in Text Block**: The generator was escaping quotes with `\\\"`, which inside the emitted file printed literal `\"`. In text blocks, double quotes do not require escaping, so `\"` resulted in `illegal character: '\'` and broken token boundaries.
3. **Broken String Concatenation**: Trying to concatenate dynamic values like `+ Instant.now() +` inside the raw block delimiters broke the string literal boundaries.

---

## 3. Solution Applied

1. **Replaced Text Block with Clean String Concatenation**:
   In `RateLimitGenerator.java`, replaced the problematic pseudo-text-block with standard, clean, well-formed Java string concatenation:

   ```java
   sb.append("            String json = \"{\\\"timestamp\\\":\\\"\" + Instant.now() + \"\\\",\"\n");
   sb.append("                + \"\\\"status\\\":429,\"\n");
   sb.append("                + \"\\\"error\\\":\\\"Too Many Requests\\\",\"\n");
   sb.append("                + \"\\\"message\\\":\\\"Rate limit exceeded. Try again in \" + waitSeconds + \" seconds.\\\",\"\n");
   sb.append("                + \"\\\"retryAfter\\\":\" + waitSeconds + \",\"\n");
   sb.append("                + \"\\\"path\\\":\\\"\" + uri + \"\\\"}\";\n");
   sb.append("            response.getWriter().write(json);\n");
   ```

2. **Added Spec #27 Traceability Mapping**:
   Mapped `RateLimitingFilter.java` using `TraceableSourceWriter` so that any future compiler or runtime issues will accurately identify `RateLimitGenerator` and trace back to the `@rateLimit` declaration in `schema.zen`.

---

## 4. Verification

- `mvn compile` in `target/studio-run`: **BUILD SUCCESS** (all 42 source files compiled cleanly with 0 errors).
- `mvn test-compile` in `target/studio-run`: **BUILD SUCCESS**.
- Rebuilt CLI binary: `bin\zenith.jar` updated.
- Ran Feature #27 E2E diagnostic test suite: **10/10 Passed (100%)**.

