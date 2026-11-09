package dev.zenith.ast;

import java.util.HashMap;
import java.util.Map;

public class AppConfig {
    private final Map<String, Object> properties;

    public AppConfig(Map<String, Object> properties) {
        this.properties = properties != null ? properties : new HashMap<>();
    }

    public static AppConfig defaults() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("app", "ZenithApplication");
        defaults.put("package", "com.example.app");
        defaults.put("basePath", "/api");
        defaults.put("port", 8080);
        defaults.put("database", "postgres");
        defaults.put("springBootVersion", "3.2.5");
        defaults.put("javaVersion", 17);
        return new AppConfig(defaults);
    }

    public String getString(String key, String defaultValue) {
        Object val = properties.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object val = properties.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public String getApp() {
        return getString("app", "ZenithApplication");
    }

    public String getPackage() {
        return getString("package", "com.example.app");
    }

    public String getBasePath() {
        String base = getString("basePath", "/api");
        if (!base.startsWith("/")) base = "/" + base;
        if (base.endsWith("/") && base.length() > 1) base = base.substring(0, base.length() - 1);
        return base;
    }

    public int getPort() {
        return getInt("port", 8080);
    }

    public String getDatabase() {
        return getString("database", "postgres");
    }

    public String getSpringBootVersion() {
        return getString("springBootVersion", "3.2.5");
    }

    public int getJavaVersion() {
        return getInt("javaVersion", 17);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = properties.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val != null) return Boolean.parseBoolean(val.toString());
        return defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        Object val = properties.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val != null) {
            try { return Long.parseLong(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public boolean isAuthEnabled() {
        String auth = getString("auth", null);
        return "jwt".equalsIgnoreCase(auth);
    }

    public boolean isSwaggerEnabled() {
        return getBoolean("swagger", false);
    }

    public boolean isFlywayEnabled() {
        return getBoolean("flyway", false);
    }

    public String getJwtSecret() {
        String raw = getString("jwtSecret", null);
        if (raw == null || raw.trim().isEmpty() || "auto".equalsIgnoreCase(raw.trim())) {
            return deriveAppJwtSecret(getApp() + ":" + getPackage());
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("${")) {
            return trimmed;
        }
        // If user provided a human-readable passphrase (< 32 chars), deterministically stretch via SHA-256 to 64 hex chars (256 bits)
        if (trimmed.length() < 32) {
            return deriveAppJwtSecret(getApp() + ":" + trimmed);
        }
        return trimmed;
    }

    public String getRawJwtSecret() {
        return getString("jwtSecret", null);
    }

    private static String deriveAppJwtSecret(String seed) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(("zenith-hmac-sha256:" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        }
    }

    public long getJwtExpirationMs() {
        Object val = properties.get("jwtExpiration");
        if (val == null) {
            for (Map.Entry<String, Object> e : properties.entrySet()) {
                if ("jwtExpiration".equalsIgnoreCase(e.getKey())) {
                    val = e.getValue();
                    break;
                }
            }
        }
        if (val != null) {
            return parseDurationMs(val.toString().trim(), 86400000L);
        }
        return 86400000L; // 24 hours
    }

    /**
     * Parses either raw milliseconds (e.g. {@code 86400000}) or human-friendly duration strings
     * ({@code 30s}, {@code 15m}, {@code 24h}, {@code 7d}, {@code 500ms}).
     */
    public static long parseDurationMs(String raw, long defaultMs) {
        if (raw == null || raw.isEmpty()) return defaultMs;
        String s = raw.trim().toLowerCase();
        try {
            if (s.endsWith("ms")) {
                return Long.parseLong(s.substring(0, s.length() - 2).trim());
            } else if (s.endsWith("s")) {
                return Long.parseLong(s.substring(0, s.length() - 1).trim()) * 1000L;
            } else if (s.endsWith("m")) {
                return Long.parseLong(s.substring(0, s.length() - 1).trim()) * 60_000L;
            } else if (s.endsWith("h")) {
                return Long.parseLong(s.substring(0, s.length() - 1).trim()) * 3_600_000L;
            } else if (s.endsWith("d")) {
                return Long.parseLong(s.substring(0, s.length() - 1).trim()) * 86_400_000L;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultMs;
        }
    }

    public String getStorageProvider() {
        return getString("storage", "minio");
    }

    public String getStorageBucket() {
        return getString("storageBucket", "zenith-bucket");
    }

    public String getRawRateLimit() {
        Object val = properties.get("rateLimit");
        if (val == null) val = properties.get("ratelimit");
        return val != null ? val.toString().trim() : null;
    }

    public RateLimitSpec getRateLimit() {
        String raw = getRawRateLimit();
        if (raw == null || raw.isEmpty()) return null;
        return RateLimitSpec.parse(raw);
    }

    public boolean hasRateLimit() {
        return getRawRateLimit() != null && !getRawRateLimit().isEmpty();
    }

    public String getMailProvider() {
        String p = getString("mail", null);
        if (p == null) p = getString("mailProvider", "console");
        return p != null ? p.toLowerCase() : "console";
    }

    public String getMailFrom() {
        String from = getString("mailFrom", null);
        if (from == null) from = getString("mail_from", "noreply@example.com");
        return from;
    }

    public boolean hasMail() {
        return properties.containsKey("mail") || properties.containsKey("mailProvider") || properties.containsKey("mailFrom");
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void set(String key, Object value) {
        this.properties.put(key, value);
    }
}
