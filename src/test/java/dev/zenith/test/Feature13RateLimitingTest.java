package dev.zenith.test;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.ast.RateLimitSpec;
import dev.zenith.ast.ZenithFile;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.parser.ParseException;

import java.util.List;

import static dev.zenith.test.TestHarness.*;

/**
 * Feature #13: API Rate Limiting (`@rateLimit`) Opaque-Box Tests.
 *
 * Tier 1: DSL Parsing & AST Modeling
 * Tier 2: Semantic Validation & Boundaries
 * Tier 3: Spring Boot & Bucket4j Generator
 * Tier 4: Spring Security Chain & Filter Integration
 */
public class Feature13RateLimitingTest {

    private static String source(List<JavaSourceFile> files, String suffix) {
        for (JavaSourceFile s : files) {
            if (s.getRelativePath().replace('\\', '/').endsWith(suffix)) {
                return s.getContent();
            }
        }
        throw new AssertionError("Generated file not found with suffix: " + suffix);
    }

    // =========================================================================
    // Tier 1: DSL Parsing & AST Modeling
    // =========================================================================

    public static void T1_01_parse_endpoint_rate_limit_string() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        rateLimit: "10/minute"
                    }
                }
            }
            """;
        ZenithFile f = parse(dsl);
        var cep = f.findModel("Order").getCustomEndpoints().get(0);
        assertTrue(cep.hasRateLimit(), "custom endpoint has rate limit");
        RateLimitSpec spec = cep.getRateLimit();
        assertEquals(10, spec.getCapacity(), "capacity");
        assertEquals("minute", spec.getUnit(), "unit");
        assertEquals(60L, spec.getDurationSeconds(), "duration seconds");
        assertEquals("10/minute", spec.toDslString(), "DSL representation");
    }

    public static void T1_02_parse_endpoint_rate_limit_units() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    get /sec { rateLimit: "5/second" }
                    get /min { rateLimit: "60/minute" }
                    get /hr { rateLimit: "1000/hour" }
                    get /day { rateLimit: "5000/day" }
                }
            }
            """;
        ZenithFile f = parse(dsl);
        var endpoints = f.findModel("Order").getCustomEndpoints();
        assertEquals(1L, endpoints.get(0).getRateLimit().getDurationSeconds(), "second");
        assertEquals(60L, endpoints.get(1).getRateLimit().getDurationSeconds(), "minute");
        assertEquals(3600L, endpoints.get(2).getRateLimit().getDurationSeconds(), "hour");
        assertEquals(86400L, endpoints.get(3).getRateLimit().getDurationSeconds(), "day");
    }

    public static void T1_03_parse_endpoint_decorator_rate_limit() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    @rateLimit("25/minute")
                    post /fast-checkout {
                    }
                }
            }
            """;
        ZenithFile f = parse(dsl);
        var cep = f.findModel("Order").getCustomEndpoints().get(0);
        assertTrue(cep.hasRateLimit(), "decorator attached rate limit");
        assertEquals(25, cep.getRateLimit().getCapacity(), "capacity");
        assertEquals("minute", cep.getRateLimit().getUnit(), "unit");
    }

    public static void T1_04_parse_endpoint_decorator_named_args() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    @rateLimit(requests = 50, per = "1m")
                    post /named-checkout {
                    }
                }
            }
            """;
        ZenithFile f = parse(dsl);
        var cep = f.findModel("Order").getCustomEndpoints().get(0);
        assertTrue(cep.hasRateLimit(), "decorator attached rate limit");
        assertEquals(50, cep.getRateLimit().getCapacity(), "capacity");
        assertEquals("minute", cep.getRateLimit().getUnit(), "unit");
    }

    public static void T1_05_parse_crud_rate_limit() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    crud {
                        rateLimit: "100/minute"
                    }
                }
            }
            """;
        ZenithFile f = parse(dsl);
        var crud = f.findModel("Order").getCrudConfig();
        assertTrue(crud.hasRateLimit(), "crud has rate limit");
        assertEquals(100, crud.getRateLimit().getCapacity(), "capacity");
        assertEquals(60L, crud.getRateLimit().getDurationSeconds(), "duration");
    }

    public static void T1_06_parse_global_config_rate_limit() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
                rateLimit: "500/minute"
            }
            model Order {
                id: UUID @id @auto
            }
            """;
        ZenithFile f = parse(dsl);
        assertTrue(f.getConfig().hasRateLimit(), "config has rate limit");
        assertEquals(500, f.getConfig().getRateLimit().getCapacity(), "global capacity");
        assertEquals("500/minute", f.getConfig().getRateLimit().toDslString(), "dsl string");
        assertTrue(f.hasRateLimiting(), "zenithFile flags rate limiting active");
    }

    // =========================================================================
    // Tier 2: Semantic Validation & Boundaries
    // =========================================================================

    public static void T2_01_reject_zero_capacity_rate_limit() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        rateLimit: "0/minute"
                    }
                }
            }
            """;
        try {
            parse(dsl);
            throw new AssertionError("Expected ParseException for zero capacity");
        } catch (ParseException ex) {
            assertContains(ex.getMessage().toLowerCase(), "greater than zero", "zero capacity error message");
        }
    }

    public static void T2_02_reject_negative_capacity_rate_limit() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        rateLimit: "-5/minute"
                    }
                }
            }
            """;
        try {
            parse(dsl);
            throw new AssertionError("Expected ParseException for negative capacity");
        } catch (ParseException ex) {
            assertTrue(ex.getMessage() != null, "negative capacity error message");
        }
    }

    public static void T2_03_reject_invalid_time_unit() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        rateLimit: "10/century"
                    }
                }
            }
            """;
        try {
            parse(dsl);
            throw new AssertionError("Expected ParseException for unsupported time unit");
        } catch (ParseException ex) {
            assertContains(ex.getMessage().toLowerCase(), "unsupported", "unsupported time unit error");
        }
    }

    public static void T2_04_reject_malformed_rate_limit_string() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        rateLimit: "invalid-syntax"
                    }
                }
            }
            """;
        try {
            parse(dsl);
            throw new AssertionError("Expected ParseException for malformed string");
        } catch (ParseException ex) {
            assertContains(ex.getMessage().toLowerCase(), "invalid", "malformed syntax error");
        }
    }

    public static void T2_05_field_annotation_rejects_typo_with_suggestion() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                total: Decimal @ratelimits
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected AnalysisException for @ratelimits typo");
        } catch (AnalysisException ex) {
            assertContains(ex.getMessage(), "@rateLimit", "@ratelimits typo suggests @rateLimit");
        }
    }

    // =========================================================================
    // Tier 3: Spring Boot & Bucket4j Generator
    // =========================================================================

    public static void T3_01_pom_includes_bucket4j_when_rate_limiting_enabled() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
                rateLimit: "100/minute"
            }
            model Order {
                id: UUID @id @auto
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String pom = source(files, "pom.xml");
        assertContains(pom, "<groupId>com.bucket4j</groupId>", "Bucket4j groupId");
        assertContains(pom, "<artifactId>bucket4j-core</artifactId>", "Bucket4j artifactId");
        assertContains(pom, "spring-boot-starter-security", "Spring Security starter added for rate limit filter");
    }

    public static void T3_02_pom_omits_bucket4j_when_no_rate_limiting() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String pom = source(files, "pom.xml");
        assertFalse(pom.contains("bucket4j-core"), "no bucket4j dependency when unused");
    }

    public static void T3_03_generates_rate_limiting_filter_class() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        rateLimit: "10/minute"
                    }
                }
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String filter = source(files, "security/RateLimitingFilter.java");
        assertContains(filter, "public class RateLimitingFilter extends OncePerRequestFilter", "OncePerRequestFilter subclass");
        assertContains(filter, "io.github.bucket4j.Bucket", "Bucket4j import");
        assertContains(filter, "X-Rate-Limit-Limit", "X-Rate-Limit-Limit header");
        assertContains(filter, "X-Rate-Limit-Remaining", "X-Rate-Limit-Remaining header");
        assertContains(filter, "Retry-After", "Retry-After header");
        assertContains(filter, "HttpStatus.TOO_MANY_REQUESTS", "HTTP 429 status code");
    }

    public static void T3_04_client_ip_and_proxy_header_resolution() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
                rateLimit: "50/minute"
            }
            model Order {
                id: UUID @id @auto
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String filter = source(files, "security/RateLimitingFilter.java");
        assertContains(filter, "X-Forwarded-For", "inspects X-Forwarded-For proxy header");
        assertContains(filter, "X-Real-IP", "inspects X-Real-IP reverse proxy header");
        assertContains(filter, "request.getRemoteAddr()", "falls back to remote client IP");
        assertContains(filter, "auth.getName()", "authenticated principal keying");
    }

    // =========================================================================
    // Tier 4: Spring Security Chain & Filter Integration
    // =========================================================================

    public static void T4_01_security_config_wires_rate_limiting_filter() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
                auth: jwt
            }
            model User {
                id: UUID @id @auto
                email: String @email @unique
                password: String @password
                role: String @role
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        rateLimit: "5/second"
                    }
                }
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String secConfig = source(files, "config/SecurityConfig.java");
        assertContains(secConfig, "RateLimitingFilter rateLimitingFilter", "injected RateLimitingFilter parameter");
        assertContains(secConfig, ".addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)", "filter registered in chain");
    }

    public static void T4_02_standalone_rate_limit_security_config_when_no_auth() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        rateLimit: "5/second"
                    }
                }
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String secConfig = source(files, "config/RateLimitSecurityConfig.java");
        assertContains(secConfig, "public class RateLimitSecurityConfig", "standalone rate limit security config created");
        assertContains(secConfig, ".addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)", "filter registered");
    }

    public static void T4_03_route_matching_combines_crud_and_custom_endpoints() {
        String dsl = """
            config {
                app: "RateApp"
                package: "com.example.rate"
                basePath: "/api/v1"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    crud {
                        rateLimit: "200/hour"
                    }
                    post /custom-action {
                        rateLimit: "5/minute"
                    }
                }
            }
            """;
        List<JavaSourceFile> files = generateAllSources(dsl);
        String filter = source(files, "security/RateLimitingFilter.java");
        assertContains(filter, "uri.matches(\"/api/v1/orders/custom-action\")", "custom endpoint route matching");
        assertContains(filter, "uri.startsWith(\"/api/v1/orders\")", "crud route matching");
        assertContains(filter, "Duration.ofSeconds(60)", "custom endpoint duration");
        assertContains(filter, "Duration.ofSeconds(3600)", "crud duration");
    }
}
