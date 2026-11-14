# ISSUE-002: Raw JPA Entity Allowed as Custom Endpoint Return / Body Type Instead of `<ModelName>Response` / `Request` DTO

| Metadata Field | Value |
| :--- | :--- |
| **Issue ID** | `ISSUE-002` |
| **Date Discovered** | `2026-11-07` |
| **Date Fixed** | `2026-11-07` |
| **Status** | `FIXED` |
| **Tested** | `YES (Verified via CLI & Studio Semantic Analyzer)` |
| **Severity** | `P1 (High — Architectural Boundary & Compile-Time Safety)` |
| **Affected Files** | [SemanticAnalyzer.java](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/analyzer/SemanticAnalyzer.java), [studio/index.html](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/resources/studio/index.html), [docs/index.html](file:///c:/Users/wergh/Desktop/apiRestLanguage/docs/index.html) |

---

## 1. Problem Description

When declaring a custom endpoint inside a `model <ModelName>` block (for example `post "/{id}/cancel" -> CustomerOrder`), developers might mistakenly write the raw `model` name (`CustomerOrder`) as the endpoint return type or request body type instead of the auto-generated DTO (`CustomerOrderResponse`, `CreateCustomerOrderRequest`, `UpdateCustomerOrderRequest`).

Previously:
1. `SemanticAnalyzer.java` did not validate custom endpoint return types (`endpoint.getReturnType()`) and explicitly allowed `modelNames.contains(bt)` for request body types (`endpoint.getBodyType()`).
2. Because Zenith's generated REST Controllers and Service interfaces strictly adhere to enterprise best practices—never exposing JPA `@Entity` classes directly over REST and only importing `<basePkg>.dto.*`—allowing a raw entity name in `.zen` caused Zenith Studio to report `✓ Valid Schema` while the generated Spring Boot project failed during Maven compilation (`cannot find symbol: class CustomerOrder` in `CustomerOrderController.java` and `CustomerOrderService.java`).

---

## 2. Root Cause & Architectural Rule

- **Architectural Rule**: REST APIs must never return or accept raw JPA `@Entity` models. For every `model <ModelName>`, Zenith automatically generates DTO records whose names **always end with `Response` or `Request`**:
  - `<ModelName>Response`
  - `Create<ModelName>Request`
  - `Update<ModelName>Request`
- Custom endpoints must return `-> <ModelName>Response`, `-> List<<ModelName>Response>`, a declared custom `dto`, or `-> Void`.

---

## 3. Resolution Implemented

1. **Compile-Time Entity Leak Detection in [SemanticAnalyzer.java](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/analyzer/SemanticAnalyzer.java)**:
   - Rejects any custom endpoint whose return type (`-> <ModelName>` or `-> List<<ModelName>>`) matches a declared `model` entity, throwing an `AnalysisException` with exact file and line number suggesting `-> <ModelName>Response` (or `-> List<<ModelName>Response>`), a custom `dto`, or `-> Void`.
   - Rejects any custom endpoint whose request body (`(body: <ModelName>)`) is a raw `model` entity, instructing the user to use `Create<ModelName>Request`, `Update<ModelName>Request`, or a custom `dto`.
   - Rejects any explicit `return <ModelName> { ... }` statement inside an `action` block that attempts to construct and return a raw entity instead of a DTO.
   - Rejects unknown/undeclared return types that are neither a declared `dto`, an auto-generated `<ModelName>Response` / `Request` DTO, a scalar type, nor `Void`.
2. **Documentation & Studio Reference Warnings**:
   - Added explicit architectural warning callouts in [studio/index.html](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/resources/studio/index.html) (Sections 6 & 8) and [docs/index.html](file:///c:/Users/wergh/Desktop/apiRestLanguage/docs/index.html) (Sections 7 & 9) stating that auto-generated DTOs for a model always end with `<ModelName>Response` or `Request` and that raw JPA entities cannot be returned in REST endpoints.

