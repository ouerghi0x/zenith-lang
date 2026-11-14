# ISSUE-009: Unfriendly Manual 64-Character Hex `jwtSecret` & Raw Millisecond `jwtExpiration` Requirement in `config`

**Issue ID**: `ISSUE-009`  
**Severity**: **P1 (High / Developer Experience & Ergonomics)**  
**Status**: **FIXED & VERIFIED**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.ast.AppConfig`, `dev.zenith.parser.Parser`, `dev.zenith.analyzer.SemanticAnalyzer`

---

## 1. Problem Description & Symptom

To configure JWT authentication in `config { ... }`, developers were previously forced to type or copy-paste a cryptic 64-character hex string and calculate raw milliseconds manually:

```zenith
config {
    app: "ShopSphere"
    package: "com.shopsphere.backend"
    basePath: "/api/v1"
    port: 8081
    database: postgres
    auth: jwt
    jwtSecret: "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
    jwtExpiration: 30000
}
```

### Why This Was Unrealistic
1. **Unrealistic 64-Char Secret Requirement**: If a developer typed a human-readable secret like `jwtSecret: "my-shop-secret"`, `SemanticAnalyzer` rejected the schema with:
   `Configuration 'jwtSecret' must be at least 256 bits (32 characters) for secure HMAC-SHA256 signing.`
2. **Unreadable Millisecond Math**: Developers had to calculate `86400000` for 24 hours, `604800000` for 7 days, or `30000` for 30 seconds instead of writing `24h`, `7d`, `30m`, or `30s`.

---

## 2. Solution: Two Clean Options (Backward-Compatible)

We preserved **Option 1 (Explicit Raw Key & Milliseconds)** for advanced/enterprise overrides while introducing **Option 2 (Zero-Config / Human-Friendly Passphrases & Duration Units)**:

### Option 1 (Existing — Explicit 256-bit Secret & Raw Milliseconds)
```zenith
config {
    app: "ShopSphere"
    auth: jwt
    jwtSecret: "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
    jwtExpiration: 86400000
}
```

### Option 2 (New — Clean & Ergonomic)
1. **Zero-Config (`auth: jwt` only — omit `jwtSecret` and `jwtExpiration` completely!)**:
   ```zenith
   config {
       app: "ShopSphere"
       package: "com.shopsphere.backend"
       auth: jwt
   }
   ```
   - Zenith automatically derives a unique, deterministic 256-bit (64-hex-char) HMAC-SHA256 signing key from the app's identity (`SHA-256("zenith-hmac-sha256:" + app + ":" + package)`), defaults `jwtExpiration` to `24h` (`86400000` ms), and binds `${JWT_SECRET:...}` in `application.yml` for production environment overrides.
2. **Human-Readable Passphrase + Duration Shorthand (`24h`, `30m`, `7d`, `30s`)**:
   ```zenith
   config {
       app: "ShopSphere"
       auth: jwt
       jwtSecret: "shopsphere-secret"   // Automatically stretched via SHA-256 to 256-bit key!
       jwtExpiration: 24h               // Supports 30s, 15m, 24h, 7d (or quoted "24h" or raw ms)
   }
   ```

---

## 3. Implementation Details

1. **`AppConfig.java`**:
   - `getJwtSecret()`:
     - When omitted or set to `"auto"`, derives a deterministic 64-character hex SHA-256 key from `app + ":" + package`.
     - When given a short human-readable passphrase (`< 32` chars), deterministically stretches it via SHA-256 to a 64-character hex 256-bit key so `Keys.hmacShaKeyFor(...)` never throws `WeakKeyException`.
     - When given a `>= 32` char key, preserves it verbatim.
   - `parseDurationMs(String raw, long defaultMs)`:
     - Converts `ms`, `s`, `m`, `h`, `d` suffixes (e.g. `30s` -> `30000`, `15m` -> `900000`, `24h` -> `86400000`, `7d` -> `604800000`) as well as plain millisecond numbers.
2. **`Parser.java`**:
   - Enhanced `parseConfig()` to accept unquoted duration literals on `jwtExpiration` (e.g. `jwtExpiration: 24h`, `jwtExpiration: 30m`, `jwtExpiration: 30s`) in addition to quoted strings (`"24h"`) and raw millisecond integers (`30000`).
3. **`SemanticAnalyzer.java`**:
   - Removed the rigid 32-character rejection error since `AppConfig.getJwtSecret()` now transparently stretches short passphrases into full 256-bit HMAC-SHA256 keys.

---

## 4. Verification

Added `T2_13_jwt_ergonomic_secret_and_duration_options` in `Feature2DatabaseIndexesTest.java`, verifying Option 2A (zero-config `auth: jwt`), Option 2B (`jwtSecret: "shopsphere-dev-key"` + `jwtExpiration: 24h`), and Option 1 (explicit 64-char hex + `30000`).

