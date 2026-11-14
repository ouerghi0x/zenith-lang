# ISSUE-010: Unified Role-Based Access Control (RBAC) Syntax Across API Endpoints

**Issue ID**: `ISSUE-010`  
**Severity**: **P2 (Design / Ergonomics / Syntax Unification)**  
**Status**: **FIXED & VERIFIED**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.parser.Parser`, `dev.zenith.ast.CrudConfig`, `dev.zenith.ast.CustomEndpointDef`, `dev.zenith.analyzer.SemanticAnalyzer`, `dev.zenith.generator.SecurityGenerator`

---

## 1. Problem Description & Current State

Controlling role access to APIs in Zenith currently suffers from multiple overlapping, fragmented syntax styles across different block scopes:

1. **Inside custom endpoints**:
   - `auth: [ADMIN, MANAGER]`
   - `roles: [ADMIN]`
   - `guard: role in [ADMIN, LOGISTICS], "Unauthorized"`
   - `guard: role == ADMIN, "Admin only"`
   - `public: true` / `auth: false`
2. **Inside CRUD blocks**:
   - `roles: [ADMIN, MANAGER]`
   - `crud { list: public, create: [ADMIN], delete: [ADMIN] }`
   - `crud { role: ADMIN }`
   - `crud { guard: role in [ADMIN] }`

### Why This Is Problematic

- **Cognitive Overhead**: Developers must remember whether to write `auth: [...]`, `roles: [...]`, `guard: role in [...]`, or `public: true`.
- **Inconsistent Mental Model**: Having 4+ different ways to declare role permissions confuses developers, clutters documentation and error diagnostics, and complicates IDE tooling.
- **Ambiguity between Authentication and Authorization**: `auth: [ADMIN]` conflates *authentication* (is the caller logged in?) with *authorization* (which authorities/roles are allowed?).
- **Guard Misuse**: `guard:` was architected for domain business validation rules (e.g., `guard: status == PENDING`), but is frequently overloaded as a makeshift security check.

---

## 2. The Unified Standard: Simple, Predictable RBAC

To establish **one clean, canonical standard** across the entire language, we define two distinct, complementary attributes:

1. **`auth: true | false`** &rarr; Controls **Authentication** (Identity Verification).
   - `auth: false` &rarr; Open to the public (no JWT token required).
   - `auth: true` &rarr; Requires a valid authenticated user (any role).
2. **`roles: [ROLE1, ROLE2]`** &rarr; Controls **Authorization** (Permissions & Authorities).
   - Specifies the exact roles permitted to call the endpoint.
   - Automatically implies `auth: true` (anonymous users are rejected with `401 Unauthorized`; authenticated users lacking the required role are rejected with `403 Forbidden`).
   - Supports both arrays (`roles: [ADMIN, MANAGER]`) and single roles (`roles: ADMIN`).
   - Disallows pseudo-roles like `roles: public` or `roles: authenticated` in custom endpoints.

3. **`guard:` is strictly for Domain Rules**:
   - `guard:` expressions are reserved exclusively for business state checks (`guard: totalAmount > 0`), never for role checks.

---

## 3. Concrete Syntax Specifications

### A. Custom Endpoints (`post`, `get`, `put`, `delete`, `patch`)

Custom routes use `roles: [...]` for role permissions, and `auth: false` for public endpoints:

```zenith
model Order {
    endpoints {
        // 1. Role-restricted endpoint (implicitly requires authentication)
        // Returns 401 if unauthenticated; returns 403 if user lacks required role
        post "/{id}/ship" -> OrderResponse {
            roles: [LOGISTICS, ADMIN]
        }

        // 2. Single role shorthand (no brackets needed)
        delete "/{id}" -> Void {
            roles: ADMIN
        }

        // 3. Any authenticated user (regardless of role)
        post "/{id}/cancel" -> Void {
            auth: true
            guard: status == OrderStatus.PENDING, "Only pending orders can be cancelled"
        }

        // 4. Public endpoint (no JWT required)
        get "/track/{orderNumber}" -> OrderResponse {
            auth: false
        }
    }
}
```

> **Rejected Anti-Patterns in Custom Endpoints**:
> - ❌ `roles: public` &rarr; Rejected by compiler (use `auth: false` instead).
> - ❌ `roles: authenticated` &rarr; Rejected by compiler (use `auth: true` instead).
> - ❌ `guard: role in [ADMIN]` &rarr; Soft-deprecated / warning (use `roles: [ADMIN]` instead).

---

### B. CRUD Blocks (`crud { ... }`)

Inside `crud { ... }`, the same `roles:` attribute applies at model-level, with granular per-operation overrides:

```zenith
model Product {
    endpoints {
        crud {
            // Default baseline roles for all CRUD operations on this model
            roles: [ADMIN, MANAGER]

            // Granular per-operation overrides:
            // 'public' is permitted here as a clean shorthand for unauthenticated operations
            list: public
            get: public
            create: [ADMIN, MANAGER]
            update: [ADMIN, MANAGER]
            delete: ADMIN
        }
    }
}
```

---

## 4. HTTP Status Code Contract

When role-based access control is enforced:

| Caller State | Endpoint Configuration | HTTP Response | Reason |
| :--- | :--- | :--- | :--- |
| **Anonymous (No Token)** | `auth: false` or `list: public` | `200 OK` / `201 Created` | Public route |
| **Anonymous (No Token)** | `auth: true` or `roles: [ADMIN]` | `401 Unauthorized` | Missing or invalid JWT token |
| **Authenticated (`USER`)** | `auth: true` | `200 OK` / `201 Created` | Valid user identity |
| **Authenticated (`USER`)** | `roles: [ADMIN]` | `403 Forbidden` | Valid user, but lacks required role authority |

---

## 5. Key Architectural Benefits

1. **Single Mental Model**: Developers learn `roles: [...]` and `auth: true/false` once and use them identically across all models and endpoints.
2. **Clear Separation of Concerns**:
   - `config { auth: jwt }` enables Spring Security 6 infrastructure.
   - `auth: true/false` toggles identity verification.
   - `roles: [...]` configures Spring Security `hasAnyAuthority(...)` authorization.
   - `guard: ...` is kept pure for domain business invariants.
3. **Graceful Deprecation Path**:
   - Existing legacy syntax (`auth: [ROLES]`, `guard: role in [...]`) remains supported with non-breaking compiler warnings pointing to `roles: [...]`.
   - Zenith Studio linting and auto-complete exclusively surface the new `roles:` standard.

---

## 6. Implementation Checklist (When Scheduled)

- [ ] **Parser (`Parser.java`)**:
  - Accept `roles: [...]` and `roles: IDENTIFIER` as the primary authorization property in `parseCustomEndpoint()`.
  - Disallow `roles: public` and `roles: authenticated` in custom endpoints with helpful compiler guidance (`"Use 'auth: false' or 'auth: true' instead"`).
- [ ] **Semantic Analyzer (`SemanticAnalyzer.java`)**:
  - Validate that every role listed under `roles:` exists in defined enums or user model role annotations.
  - Issue warning when `guard:` contains `role in [...]` or `role == ...`.
- [ ] **Spring Security Generator (`SecurityGenerator.java`)**:
  - Wire `roles:` directly into Spring Security 6 `AuthorizeHttpRequestsConfigurer` (`requestMatchers(...).hasAnyAuthority(...)`).
- [ ] **Test Suite (`Feature10UnifiedRbacTest.java`)**:
  - Add opaque-box tests covering Tiers 1-4 for `roles: [...]`, single-role shorthand, `auth: false`, and 401/403 HTTP status mappings.
- [ ] **Documentation**:
  - Update `docs/index.html`, `README.md`, and Studio Reference Guide with the unified standard.
