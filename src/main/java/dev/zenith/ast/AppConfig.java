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
        if ("jwt".equalsIgnoreCase(auth) || "true".equalsIgnoreCase(auth)) return true;
        return getBoolean("auth", false);
    }

    public boolean isSwaggerEnabled() {
        return getBoolean("swagger", true);
    }

    public boolean isFlywayEnabled() {
        return getBoolean("flyway", true);
    }

    public String getJwtSecret() {
        return getString("jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
    }

    public long getJwtExpirationMs() {
        return getLong("jwtExpiration", 86400000L); // 24 hours
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void set(String key, Object value) {
        this.properties.put(key, value);
    }
}
