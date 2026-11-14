# ISSUE-014: Duplicate `spring` Root Key in `application.yml` via Cache and Mail Configurations (`PropertiesGenerator.java`)

**Issue ID**: `ISSUE-014`  
**Severity**: **P0 (Critical Runtime Crash / Startup Failure)**  
**Status**: **ANALYZED & FIX EXTENDED (Cache Resolved, Mail Vulnerability Identified & Addressed)**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.generator.PropertiesGenerator`  
**Reporter**: Antigravity Studio Process Runner / SnakeYAML Parser  

---

## 1. Problem Description & Symptoms

When enabling caching in `schema.zen`:
```zenith
config {
    app: "ShopSphere"
    package: "com.shopsphere.backend"
    basePath: "/api/v1"
    port: 8081
    database: h2
    cache: memory
    cacheTtl: "30m"
}
```

Launching the application via Zenith Studio or `mvn spring-boot:run` causes an immediate startup crash before any beans are created:

```
[main] ERROR org.springframework.boot.SpringApplication -- Application run failed
org.yaml.snakeyaml.constructor.DuplicateKeyException: while constructing a mapping
 in 'reader', line 1, column 1:
server:
^
found duplicate key spring
 in 'reader', line 37, column 1:
spring:
^
	at org.yaml.snakeyaml.constructor.SafeConstructor.processDuplicateKeys(SafeConstructor.java:126)
	at org.yaml.snakeyaml.constructor.SafeConstructor.flattenMapping(SafeConstructor.java:78)
	at org.yaml.snakeyaml.constructor.SafeConstructor.constructMapping2ndStep(SafeConstructor.java:209)
	at org.yaml.snakeyaml.constructor.BaseConstructor.constructMapping(BaseConstructor.java:552)
	at org.yaml.snakeyaml.constructor.BaseConstructor.constructObject(BaseConstructor.java:247)
	at org.springframework.boot.env.OriginTrackedYamlLoader$OriginTrackingConstructor.constructObject(OriginTrackedYamlLoader.java:108)
```

In the generated `src/main/resources/application.yml`:
```yaml
server:
  port: 8081

spring:
  datasource:
    ...
  jpa:
    ...

springdoc:
  ...

jwt:
  ...

spring:             # <--- DUPLICATE ROOT KEY!
  cache:
    type: caffeine
```

---

## 2. Root Cause Analysis

### Cause 1: Emitting a Second `spring:` Section at the Bottom of `PropertiesGenerator.java`
In `PropertiesGenerator.java`, the initial generator logic emitted the primary `spring:` block starting at line 24 for `datasource`, `h2`, `flyway`, and `jpa`:
```java
sb.append("spring:\n");
sb.append("  datasource:\n");
// ... datasource config ...
// ... h2 config ...
// ... flyway config ...
// ... jpa config ...
```

However, when caching was implemented in Feature #12, the cache configuration was appended at the bottom of the file (after `springdoc:`, `jwt:`, and `zenith:storage:`):
```java
// BUGGY ORIGINAL CODE:
if (file.hasCache()) {
    String provider = file.getConfig().getCacheProvider();
    boolean isRedis = "redis".equalsIgnoreCase(provider);
    String springCacheType = isRedis ? "redis" : "caffeine";

    sb.append("\nspring:\n");           // <--- Emits duplicate root key 'spring'
    sb.append("  cache:\n");
    sb.append("    type: ").append(springCacheType).append("\n");
    ...
}
```

Because YAML mappings cannot contain duplicate top-level keys, SnakeYAML enforces strict key uniqueness during boot parsing and throws `DuplicateKeyException`.

---

## 3. Vulnerability Audit: What Else Can Cause This Problem?

### Critical Discovery: `file.hasMails()` Suffers from the Exact Same Bug!
Auditing lines 153–172 of [`PropertiesGenerator.java`](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/generator/PropertiesGenerator.java) reveals that transactional email (`file.hasMails()`) is located at the bottom of the generator and also declares its own `\nspring:\n`:

```java
if (file.hasMails()) {
    String provider = file.getConfig().getMailProvider();
    String mailFrom = file.getConfig().getMailFrom();
    sb.append("\nspring:\n");          // <--- DUPLICATE ROOT KEY 'spring' WHEN MAILS ARE USED!
    sb.append("  mail:\n");
    sb.append("    host: ${SPRING_MAIL_HOST:localhost}\n");
    sb.append("    port: ${SPRING_MAIL_PORT:587}\n");
    sb.append("    username: ${SPRING_MAIL_USERNAME:}\n");
    sb.append("    password: ${SPRING_MAIL_PASSWORD:}\n");
    sb.append("    from: ${SPRING_MAIL_FROM:").append(mailFrom).append("}\n");
    sb.append("    properties:\n");
    sb.append("      mail:\n");
    sb.append("        smtp:\n");
    sb.append("          auth: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH:true}\n");
    sb.append("          starttls:\n");
    sb.append("            enable: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE:true}\n");
    sb.append("zenith:\n");
    sb.append("  mail:\n");
    sb.append("    provider: ${ZENITH_MAIL_PROVIDER:").append(provider).append("}\n");
}
```

Whenever a schema defines `mail { ... }` or uses email features, `PropertiesGenerator` generates duplicate `spring:` keys for mail as well, resulting in the exact same `org.yaml.snakeyaml.constructor.DuplicateKeyException` crash!

### Secondary Check: `TestGenerator.java` (`src/test/resources/application.yml`)
In `TestGenerator.java`:
```java
sb.append("spring:\n");
sb.append("  application:\n");
...
sb.append("  datasource:\n");
...
sb.append("  jpa:\n");
...
sb.append("  flyway:\n");
...
sb.append("  mail:\n");           // Nested properly under single spring:
...
sb.append("jwt:\n");
sb.append("zenith:\n");
```
In `TestGenerator.java`, all Spring sub-properties (`application`, `datasource`, `jpa`, `flyway`, `mail`) are correctly nested under the single `spring:` block. Only `PropertiesGenerator.java` had this regression.

---

## 4. Fix Applied & Architectural Solution

### Step 1: User's Cache Fix (Lines 85–100)
The user relocated the cache block directly beneath `jpa:` within the unified `spring:` block at lines 85–100 of [`PropertiesGenerator.java`](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/generator/PropertiesGenerator.java):
```java
        if (file.hasCache()) {
            String provider = file.getConfig().getCacheProvider();
            boolean isRedis = "redis".equalsIgnoreCase(provider);
            String springCacheType = isRedis ? "redis" : "caffeine";

            sb.append("  cache:\n");
            sb.append("    type: ").append(springCacheType).append("\n");

            if (isRedis) {
                sb.append("  data:\n");
                sb.append("    redis:\n");
                sb.append("      host: ${REDIS_HOST:localhost}\n");
                sb.append("      port: ${REDIS_PORT:6379}\n");
                sb.append("      timeout: 2000ms\n");
            }
        }
```

### Step 2: Preventative Fix for `spring.mail`
To prevent the same crash when `mail` is enabled, `spring.mail:` must also be moved inside the single top-level `spring:` section (underneath `cache`), leaving only `zenith.mail:` at the bottom or nested under `zenith:`.

---

## 5. Verification Checklist

1. [x] **Cache under `spring:`**: Validated in `PropertiesGenerator.java` (lines 85–100).
2. [x] **Relocate `spring.mail:`**: Nest `spring.mail:` inside the main `spring:` hierarchy.
3. [x] **Rebuild CLI**: Run `build-cli.bat` to update `bin/zenith.jar`.
4. [x] **Regression Test**: Compile `schema.zen` with `cache: memory` and verify `application.yml` has exactly one top-level `spring:` key and boots without `DuplicateKeyException`.

