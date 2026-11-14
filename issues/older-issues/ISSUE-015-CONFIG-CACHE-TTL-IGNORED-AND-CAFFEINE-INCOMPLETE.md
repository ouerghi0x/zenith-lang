# ISSUE-015: Config-Level `cacheTtl` Ignored and Non-Redis Cache Providers Lack Per-Cache TTL Customization

**Issue ID**: `ISSUE-015`  
**Severity**: **P2 (High - Incomplete Feature Implementation & Specification Discrepancy)**  
**Status**: **RESOLVED & VERIFIED (Build & Compilation Verified)**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.generator.CacheConfigGenerator`, `dev.zenith.ast.AppConfig`  
**Reporter**: Antigravity Studio / User Schema Verification  

---

## 1. Problem Description & Symptoms

When configuring declarative caching in `schema.zen`:

```zenith
config {
    app: "ShopSphere"
    package: "com.shopsphere.backend"
    basePath: "/api/v1"
    port: 8081
    database: h2
    cache: redis       // Or cache: memory
    cacheTtl: "50m"    // <-- Explicit custom TTL defined in config
}
```

Two critical implementation defects occur in the generated [`CacheConfig.java`](file:///c:/Users/wergh/Desktop/apiRestLanguage/target/studio-run/src/main/java/com/shopsphere/backend/config/CacheConfig.java):

### Symptom 1: Hardcoded 10-Minute Default Ignores `config.cacheTtl`
Even though `cacheTtl: "50m"` is explicitly declared in `config`, the generated `defaultCacheConfiguration()` (for Redis) or `expireAfterWrite(...)` (for Caffeine) hardcodes `Duration.ofMinutes(10)`:

```java
@Bean
public RedisCacheConfiguration defaultCacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10)) // <--- BUG: Hardcoded 10m instead of 50m!
        .disableCachingNullValues()
        ...
}
```

And for Caffeine:
```java
@Bean
public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager();
    cacheManager.setCaffeine(Caffeine.newBuilder()
        .initialCapacity(100)
        .maximumSize(5000)
        .expireAfterWrite(Duration.ofMinutes(10))); // <--- BUG: Hardcoded 10m!
    return cacheManager;
}
```

### Symptom 2: Incomplete CacheManager Implementation for Non-Redis Providers (`memory` / `caffeine`)
While `redis` generates:
- `defaultCacheConfiguration()`
- Per-cache configurations via `Map<String, RedisCacheConfiguration> cacheConfigs` with `.withInitialCacheConfigurations(cacheConfigs)`
- Specific TTL durations parsed per endpoint (`cacheConfigs.put("order", ...)`),

The Caffeine/in-memory implementation:
- Only sets a single global builder with hardcoded 10 minutes.
- Completely ignores per-cache TTL mappings (`cacheTtls` map) collected from CRUD and custom endpoints.
- Does not register cache names or allow variable TTLs per cache bucket.

---

## 2. Root Cause Analysis

In [`CacheConfigGenerator.java`](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/generator/CacheConfigGenerator.java):

1. **Failure to read `file.getConfig().getCacheTtl()` for default TTL**:
   Lines 73–75:
   ```java
   sb.append("    @Bean\n");
   sb.append("    public RedisCacheConfiguration defaultCacheConfiguration() {\n");
   sb.append("        return RedisCacheConfiguration.defaultCacheConfig()\n");
   sb.append("            .entryTtl(Duration.ofMinutes(10))\n"); // NEVER reads file.getConfig().getCacheTtl()
   ```
   The generator never uses `Parser.parseCacheDuration(file.getConfig().getCacheTtl())` to set the default TTL for `defaultCacheConfiguration()` or Caffeine's `expireAfterWrite`.

2. **Caffeine Cache Manager Does Not Support Per-Cache TTLs**:
   In lines 130–138:
   ```java
   sb.append("    @Bean\n");
   sb.append("    public CacheManager cacheManager() {\n");
   sb.append("        CaffeineCacheManager cacheManager = new CaffeineCacheManager();\n");
   sb.append("        cacheManager.setCaffeine(Caffeine.newBuilder()\n");
   sb.append("            .initialCapacity(100)\n");
   sb.append("            .maximumSize(5000)\n");
   sb.append("            .expireAfterWrite(Duration.ofMinutes(10)));\n");
   sb.append("        return cacheManager;\n");
   sb.append("    }\n\n");
   ```
   `CaffeineCacheManager` by default applies the single `Caffeine` spec to all caches unless custom cache loaders or individual cache instances are registered, and the generator completely discards `cacheTtls` for non-Redis providers.

---

## 3. Recommended Remediation Plan

### 1. Honor `config.cacheTtl` in `CacheConfigGenerator.java`
Parse the config-level TTL:
```java
String defaultTtlStr = file.getConfig().getCacheTtl();
Duration defaultDuration = Parser.parseCacheDuration(defaultTtlStr);
long defaultSeconds = defaultDuration.getSeconds() > 0 ? defaultDuration.getSeconds() : 600;
```
- For Redis:
  ```java
  .entryTtl(Duration.ofSeconds(" + defaultSeconds + "L))
  ```
- For Caffeine:
  ```java
  .expireAfterWrite(Duration.ofSeconds(" + defaultSeconds + "L))
  ```

### 2. Implement Per-Cache TTL Customization for Caffeine
To support individual TTLs in Caffeine:
- Use Caffeine's dynamic cache builder or register named Caffeine caches in `cacheManager`:
  ```java
  CaffeineCacheManager cacheManager = new CaffeineCacheManager();
  // Register known cache names so they are eagerly configured with their specific TTLs
  ```
- Or generate a custom Caffeine `CacheLoader` / multi-cache manager if individual per-model TTLs are specified.

---

## 4. Verification Steps

1. In `schema.zen`, set `cacheTtl: "50m"`.
2. Generate project files with `bin/zenith.bat build schema.zen -o target/studio-run`.
3. Inspect `CacheConfig.java` to confirm:
   - Redis: `.entryTtl(Duration.ofSeconds(3000L))` or equivalent 50m.
   - Caffeine: `.expireAfterWrite(Duration.ofSeconds(3000L))` or equivalent 50m.

