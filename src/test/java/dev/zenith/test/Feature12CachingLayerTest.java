package dev.zenith.test;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.ast.ZenithFile;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.parser.ParseException;

import java.util.List;

import static dev.zenith.test.TestHarness.*;

/**
 * Feature #12: Caching Layer (Redis / Spring Cache / Caffeine) Opaque-Box Tests.
 *
 * Tier 1: DSL Parsing & AST Modeling
 * Tier 2: Semantic Validation & Boundaries
 * Tier 3: Spring Cache Code Generation (Redis & Caffeine)
 * Tier 4: Docker & Infrastructure Generation (Redis Container & Properties)
 */
public class Feature12CachingLayerTest {

    private static String source(List<JavaSourceFile> files, String suffix) {
        for (JavaSourceFile s : files) {
            if (s.getRelativePath().replace('\\', '/').endsWith(suffix)) {
                return s.getContent();
            }
        }
        throw new AssertionError("Generated file not found with suffix: " + suffix);
    }

    private static boolean hasFile(List<JavaSourceFile> files, String suffix) {
        for (JavaSourceFile s : files) {
            if (s.getRelativePath().replace('\\', '/').endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Tier 1: DSL Parsing & AST Modeling
    // =========================================================================

    public static void T1_01_parse_config_cache_provider_redis() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                title: String
            }
            """;
        ZenithFile f = parse(dsl);
        assertEquals("redis", f.getConfig().getCacheProvider(), "cache provider");
        assertTrue(f.getConfig().hasCache(), "hasCache");
        assertTrue(f.hasCache(), "file hasCache");
    }

    public static void T1_02_parse_config_cache_provider_caffeine() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: caffeine
            }
            model Product {
                id: UUID @id @auto
                title: String
            }
            """;
        ZenithFile f = parse(dsl);
        assertEquals("caffeine", f.getConfig().getCacheProvider(), "cache provider caffeine");
        assertTrue(f.getConfig().hasCache(), "hasCache");
    }

    public static void T1_03_parse_crud_cache_duration_string() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                name: String
                crud {
                    cache: "15m"
                }
            }
            """;
        ZenithFile f = parse(dsl);
        var crud = f.findModel("Product").getCrudConfig();
        assertTrue(crud.hasCache(), "crud has cache");
        assertEquals("15m", crud.getCacheDuration(), "cache duration");
    }

    public static void T1_04_parse_crud_cache_boolean_true() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                name: String
                crud {
                    cache: true
                }
            }
            """;
        ZenithFile f = parse(dsl);
        var crud = f.findModel("Product").getCrudConfig();
        assertTrue(crud.hasCache(), "crud has cache default");
        assertEquals("10m", crud.getCacheDuration(), "default cache duration");
    }

    public static void T1_05_parse_custom_endpoint_cache_duration() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                name: String
                endpoints {
                    get /featured -> List<ProductResponse> {
                        cache: "1h"
                    }
                }
            }
            """;
        ZenithFile f = parse(dsl);
        var ep = f.findModel("Product").getCustomEndpoints().get(0);
        assertTrue(ep.hasCache(), "endpoint has cache");
        assertEquals("1h", ep.getCacheDuration(), "endpoint cache duration");
    }

    // =========================================================================
    // Tier 2: Semantic Validation & Boundaries
    // =========================================================================

    public static void T2_01_reject_invalid_config_cache_provider() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: memcached
            }
            model Product {
                id: UUID @id @auto
            }
            """;
        try {
            parse(dsl);
            throw new AssertionError("Expected ParseException for invalid cache provider");
        } catch (ParseException ex) {
            assertContains(ex.getMessage(), "Invalid cache provider", "Error must mention invalid cache provider");
        }
    }

    public static void T2_02_reject_invalid_duration_format() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                crud {
                    cache: "abc"
                }
            }
            """;
        try {
            parse(dsl);
            throw new AssertionError("Expected ParseException for invalid duration format");
        } catch (ParseException ex) {
            assertContains(ex.getMessage(), "Invalid cache duration format", "Error must mention invalid cache duration format");
        }
    }

    public static void T2_03_reject_cache_on_non_get_custom_endpoint() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                endpoints {
                    post /create -> Void {
                        cache: "10m"
                    }
                }
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected AnalysisException for cache on POST endpoint");
        } catch (AnalysisException ex) {
            assertContains(ex.getMessage(), "Caching is only permitted on read operations", "Error must mention read operations only");
        }
    }

    public static void T2_04_allow_zero_duration_as_disabled() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                crud {
                    cache: "0s"
                }
            }
            """;
        ZenithFile f = parse(dsl);
        assertFalse(f.findModel("Product").getCrudConfig().hasCache(), "0s duration disables cache");
    }

    // =========================================================================
    // Tier 3: Spring Cache Code Generation (Redis & Caffeine)
    // =========================================================================

    public static void T3_01_generates_cache_config_for_redis() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                name: String
                crud {
                    cache: "15m"
                }
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        assertTrue(hasFile(files, "config/CacheConfig.java"), "CacheConfig.java generated");
        String cfg = source(files, "config/CacheConfig.java");
        assertTrue(cfg.contains("@EnableCaching"), "has @EnableCaching");
        assertTrue(cfg.contains("RedisCacheManager"), "has RedisCacheManager");
        assertTrue(cfg.contains("GenericJackson2JsonRedisSerializer"), "has JSON serializer");
        assertTrue(cfg.contains("CacheErrorHandler"), "has fault-tolerant CacheErrorHandler");
        assertTrue(cfg.contains("product"), "contains product cache namespace");
    }

    public static void T3_02_generates_cache_config_for_caffeine() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: caffeine
            }
            model Product {
                id: UUID @id @auto
                name: String
                crud {
                    cache: "30s"
                }
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        assertTrue(hasFile(files, "config/CacheConfig.java"), "CacheConfig.java generated");
        String cfg = source(files, "config/CacheConfig.java");
        assertTrue(cfg.contains("@EnableCaching"), "has @EnableCaching");
        assertTrue(cfg.contains("CaffeineCacheManager"), "has CaffeineCacheManager");
        assertTrue(cfg.contains("Caffeine.newBuilder()"), "configures Caffeine builder");
    }

    public static void T3_03_generates_service_cacheable_and_cache_evict() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Article {
                id: UUID @id @auto
                title: String
                content: String
                crud {
                    cache: "30m"
                }
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String service = source(files, "service/impl/ArticleServiceImpl.java");

        assertTrue(service.contains("@Cacheable(value = \"article_list\""), "getAll has @Cacheable for article_list");
        assertTrue(service.contains("@Cacheable(value = \"article\", key = \"#id\")"), "getById has @Cacheable with key #id");
        assertTrue(service.contains("@CacheEvict(value = {\"article\", \"article_list\"}, allEntries = true)"), "mutations have @CacheEvict");
    }

    public static void T3_04_generates_service_custom_endpoint_cacheable() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Category {
                id: Long @id @auto
                name: String
                endpoints {
                    get /tree -> List<CategoryResponse> {
                        cache: "2h"
                    }
                }
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String service = source(files, "service/impl/CategoryServiceImpl.java");
        assertTrue(service.contains("@Cacheable(value = \"category_tree\")"), "custom get endpoint has @Cacheable");
    }

    public static void T3_05_generates_pom_dependencies_for_redis() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
                name: String
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String pom = source(files, "pom.xml");
        assertTrue(pom.contains("spring-boot-starter-cache"), "contains starter-cache");
        assertTrue(pom.contains("spring-boot-starter-data-redis"), "contains starter-data-redis");
        assertTrue(pom.contains("commons-pool2"), "contains commons-pool2");
    }

    public static void T3_06_generates_pom_dependencies_for_caffeine() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: caffeine
            }
            model Product {
                id: UUID @id @auto
                name: String
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String pom = source(files, "pom.xml");
        assertTrue(pom.contains("spring-boot-starter-cache"), "contains starter-cache");
        assertTrue(pom.contains("caffeine"), "contains caffeine artifact");
    }

    // =========================================================================
    // Tier 4: Docker & Infrastructure Generation (Redis Container & Properties)
    // =========================================================================

    public static void T4_01_generates_application_yml_redis_properties() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: redis
            }
            model Product {
                id: UUID @id @auto
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String yml = source(files, "application.yml");
        assertTrue(yml.contains("spring:"), "contains spring");
        assertTrue(yml.contains("cache:"), "contains cache");
        assertTrue(yml.contains("type: redis"), "type is redis");
        assertTrue(yml.contains("REDIS_HOST:localhost"), "redis host configured");
        assertTrue(yml.contains("REDIS_PORT:6379"), "redis port configured");
    }

    public static void T4_02_generates_application_yml_caffeine_properties() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                cache: caffeine
            }
            model Product {
                id: UUID @id @auto
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String yml = source(files, "application.yml");
        assertTrue(yml.contains("type: caffeine"), "type is caffeine");
        assertFalse(yml.contains("REDIS_HOST"), "does not contain redis host");
    }

    public static void T4_03_generates_docker_compose_redis_service() {
        String dsl = """
            config {
                app: "CacheApp"
                package: "com.example.cache"
                database: postgres
                cache: redis
            }
            model Product {
                id: UUID @id @auto
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String docker = source(files, "docker-compose.yml");
        assertTrue(docker.contains("redis:"), "contains redis service");
        assertTrue(docker.contains("image: redis:7-alpine"), "uses redis:7-alpine");
        assertTrue(docker.contains("\"6379:6379\""), "exposes port 6379");
        assertTrue(docker.contains("REDIS_HOST: redis"), "injects REDIS_HOST env into api");
        assertTrue(docker.contains("redisdata:"), "declares redis volume");
    }
}
