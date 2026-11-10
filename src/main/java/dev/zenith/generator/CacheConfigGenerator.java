package dev.zenith.generator;

import dev.zenith.ast.CustomEndpointDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.parser.Parser;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates Spring Cache configuration for Redis or Caffeine:
 * - RedisCacheManager with JSON serializers, per-cache TTL mappings, and a fault-tolerant CacheErrorHandler.
 * - CaffeineCacheManager with expiration policies and maximum size limits.
 */
public class CacheConfigGenerator {

    public static JavaSourceFile generate(ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String provider = file.getConfig().getCacheProvider();
        if ("memory".equalsIgnoreCase(provider)) {
            provider = "caffeine";
        }
        boolean isRedis = "redis".equalsIgnoreCase(provider);

        // Gather per-cache TTLs from crud and custom endpoints
        Map<String, String> cacheTtls = new LinkedHashMap<>();
        for (ModelDef model : file.getModels()) {
            String slug = TypeHelper.toSnakeCase(model.getName()).replace('_', '-');
            if (model.getCrudConfig() != null && model.getCrudConfig().hasCache()) {
                String dur = model.getCrudConfig().getCacheDuration();
                cacheTtls.put(slug, dur);
                cacheTtls.put(slug + "_list", dur);
            }
            for (CustomEndpointDef ep : model.getCustomEndpoints()) {
                if (ep.hasCache()) {
                    String epCacheName = slug + "_" + TypeHelper.toEndpointMethodName(ep.getMethod(), ep.getPath());
                    cacheTtls.put(epCacheName, ep.getCacheDuration());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".config;\n\n");
        sb.append("import org.slf4j.Logger;\n");
        sb.append("import org.slf4j.LoggerFactory;\n");
        sb.append("import org.springframework.cache.Cache;\n");
        sb.append("import org.springframework.cache.CacheManager;\n");
        sb.append("import org.springframework.cache.annotation.CachingConfigurer;\n");
        sb.append("import org.springframework.cache.annotation.EnableCaching;\n");
        sb.append("import org.springframework.cache.interceptor.CacheErrorHandler;\n");
        sb.append("import org.springframework.context.annotation.Bean;\n");
        sb.append("import org.springframework.context.annotation.Configuration;\n");
        sb.append("import java.time.Duration;\n");

        if (isRedis) {
            sb.append("import org.springframework.data.redis.cache.RedisCacheConfiguration;\n");
            sb.append("import org.springframework.data.redis.cache.RedisCacheManager;\n");
            sb.append("import org.springframework.data.redis.connection.RedisConnectionFactory;\n");
            sb.append("import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;\n");
            sb.append("import org.springframework.data.redis.serializer.RedisSerializationContext;\n");
            sb.append("import org.springframework.data.redis.serializer.StringRedisSerializer;\n");
            sb.append("import java.util.HashMap;\n");
            sb.append("import java.util.Map;\n\n");

            sb.append("@Configuration\n");
            sb.append("@EnableCaching\n");
            sb.append("public class CacheConfig implements CachingConfigurer {\n\n");
            sb.append("    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);\n\n");

            sb.append("    @Bean\n");
            sb.append("    public RedisCacheConfiguration defaultCacheConfiguration() {\n");
            sb.append("        return RedisCacheConfiguration.defaultCacheConfig()\n");
            sb.append("            .entryTtl(Duration.ofMinutes(10))\n");
            sb.append("            .disableCachingNullValues()\n");
            sb.append("            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))\n");
            sb.append("            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));\n");
            sb.append("    }\n\n");

            sb.append("    @Bean\n");
            sb.append("    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {\n");
            sb.append("        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();\n");

            for (Map.Entry<String, String> e : cacheTtls.entrySet()) {
                Duration d = Parser.parseCacheDuration(e.getValue());
                long seconds = d.getSeconds() > 0 ? d.getSeconds() : 600;
                sb.append("        cacheConfigs.put(\"").append(e.getKey()).append("\", defaultCacheConfiguration().entryTtl(Duration.ofSeconds(").append(seconds).append("L)));\n");
            }

            sb.append("\n        return RedisCacheManager.builder(connectionFactory)\n");
            sb.append("            .cacheDefaults(defaultCacheConfiguration())\n");
            sb.append("            .withInitialCacheConfigurations(cacheConfigs)\n");
            sb.append("            .build();\n");
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public CacheErrorHandler errorHandler() {\n");
            sb.append("        return new CacheErrorHandler() {\n");
            sb.append("            @Override\n");
            sb.append("            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {\n");
            sb.append("                log.warn(\"Redis cache GET error for key '{}' in cache '{}': {}. Falling back to database.\", key, cache.getName(), exception.getMessage());\n");
            sb.append("            }\n\n");
            sb.append("            @Override\n");
            sb.append("            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {\n");
            sb.append("                log.warn(\"Redis cache PUT error for key '{}' in cache '{}': {}. Continuing execution.\", key, cache.getName(), exception.getMessage());\n");
            sb.append("            }\n\n");
            sb.append("            @Override\n");
            sb.append("            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {\n");
            sb.append("                log.warn(\"Redis cache EVICT error for key '{}' in cache '{}': {}. Continuing execution.\", key, cache.getName(), exception.getMessage());\n");
            sb.append("            }\n\n");
            sb.append("            @Override\n");
            sb.append("            public void handleCacheClearError(RuntimeException exception, Cache cache) {\n");
            sb.append("                log.warn(\"Redis cache CLEAR error in cache '{}': {}. Continuing execution.\", cache.getName(), exception.getMessage());\n");
            sb.append("            }\n");
            sb.append("        };\n");
            sb.append("    }\n");
            sb.append("}\n");
        } else {
            // Caffeine Cache
            sb.append("import org.springframework.cache.caffeine.CaffeineCacheManager;\n");
            sb.append("import com.github.benmanes.caffeine.cache.Caffeine;\n");
            sb.append("import java.util.concurrent.TimeUnit;\n\n");

            sb.append("@Configuration\n");
            sb.append("@EnableCaching\n");
            sb.append("public class CacheConfig implements CachingConfigurer {\n\n");
            sb.append("    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);\n\n");

            sb.append("    @Bean\n");
            sb.append("    public CacheManager cacheManager() {\n");
            sb.append("        CaffeineCacheManager cacheManager = new CaffeineCacheManager();\n");
            sb.append("        cacheManager.setCaffeine(Caffeine.newBuilder()\n");
            sb.append("            .initialCapacity(100)\n");
            sb.append("            .maximumSize(5000)\n");
            sb.append("            .expireAfterWrite(Duration.ofMinutes(10)));\n");
            sb.append("        return cacheManager;\n");
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public CacheErrorHandler errorHandler() {\n");
            sb.append("        return new CacheErrorHandler() {\n");
            sb.append("            @Override\n");
            sb.append("            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {\n");
            sb.append("                log.warn(\"Caffeine cache GET error for key '{}' in cache '{}': {}. Falling back to database.\", key, cache.getName(), exception.getMessage());\n");
            sb.append("            }\n\n");
            sb.append("            @Override\n");
            sb.append("            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {\n");
            sb.append("                log.warn(\"Caffeine cache PUT error for key '{}' in cache '{}': {}. Continuing execution.\", key, cache.getName(), exception.getMessage());\n");
            sb.append("            }\n\n");
            sb.append("            @Override\n");
            sb.append("            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {\n");
            sb.append("                log.warn(\"Caffeine cache EVICT error for key '{}' in cache '{}': {}. Continuing execution.\", key, cache.getName(), exception.getMessage());\n");
            sb.append("            }\n\n");
            sb.append("            @Override\n");
            sb.append("            public void handleCacheClearError(RuntimeException exception, Cache cache) {\n");
            sb.append("                log.warn(\"Caffeine cache CLEAR error in cache '{}': {}. Continuing execution.\", cache.getName(), exception.getMessage());\n");
            sb.append("            }\n");
            sb.append("        };\n");
            sb.append("    }\n");
            sb.append("}\n");
        }

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/config/CacheConfig.java";
        return new JavaSourceFile(path, sb.toString());
    }
}

