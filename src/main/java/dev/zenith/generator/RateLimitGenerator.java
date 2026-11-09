package dev.zenith.generator;

import dev.zenith.ast.CrudConfig;
import dev.zenith.ast.CustomEndpointDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.RateLimitSpec;
import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

public class RateLimitGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        if (!file.hasRateLimiting()) {
            return list;
        }

        String basePkg = file.getConfig().getPackage();
        String basePath = file.getConfig().getBasePath();

        list.add(generateRateLimitingFilter(basePkg, basePath, file));
        if (!file.hasAuth()) {
            list.add(generateRateLimitSecurityConfig(basePkg, basePath));
        }
        return list;
    }

    private static JavaSourceFile generateRateLimitSecurityConfig(String basePkg, String basePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".config;\n\n");
        sb.append("import ").append(basePkg).append(".security.RateLimitingFilter;\n");
        sb.append("import org.springframework.context.annotation.Bean;\n");
        sb.append("import org.springframework.context.annotation.Configuration;\n");
        sb.append("import org.springframework.security.config.Customizer;\n");
        sb.append("import org.springframework.security.config.annotation.web.builders.HttpSecurity;\n");
        sb.append("import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;\n");
        sb.append("import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;\n");
        sb.append("import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;\n");
        sb.append("import org.springframework.security.config.http.SessionCreationPolicy;\n");
        sb.append("import org.springframework.security.web.SecurityFilterChain;\n");
        sb.append("import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;\n\n");

        sb.append("@Configuration\n");
        sb.append("@EnableWebSecurity\n");
        sb.append("public class RateLimitSecurityConfig {\n\n");
        sb.append("    private final RateLimitingFilter rateLimitingFilter;\n\n");
        sb.append("    public RateLimitSecurityConfig(RateLimitingFilter rateLimitingFilter) {\n");
        sb.append("        this.rateLimitingFilter = rateLimitingFilter;\n");
        sb.append("    }\n\n");
        sb.append("    @Bean\n");
        sb.append("    public SecurityFilterChain rateLimitSecurityFilterChain(HttpSecurity http) throws Exception {\n");
        sb.append("        http\n");
        sb.append("            .csrf(AbstractHttpConfigurer::disable)\n");
        sb.append("            .cors(Customizer.withDefaults())\n");
        sb.append("            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))\n");
        sb.append("            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))\n");
        sb.append("            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())\n");
        sb.append("            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);\n");
        sb.append("        return http.build();\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/config/RateLimitSecurityConfig.java",
                sb.toString());
    }

    private static JavaSourceFile generateRateLimitingFilter(String basePkg, String basePath, ZenithFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".security;\n\n");
        sb.append("import io.github.bucket4j.Bandwidth;\n");
        sb.append("import io.github.bucket4j.Bucket;\n");
        sb.append("import io.github.bucket4j.ConsumptionProbe;\n");
        sb.append("import io.github.bucket4j.Refill;\n");
        sb.append("import jakarta.servlet.FilterChain;\n");
        sb.append("import jakarta.servlet.ServletException;\n");
        sb.append("import jakarta.servlet.http.HttpServletRequest;\n");
        sb.append("import jakarta.servlet.http.HttpServletResponse;\n");
        sb.append("import org.springframework.http.HttpStatus;\n");
        sb.append("import org.springframework.http.MediaType;\n");
        sb.append("import org.springframework.security.core.Authentication;\n");
        sb.append("import org.springframework.security.core.context.SecurityContextHolder;\n");
        sb.append("import org.springframework.stereotype.Component;\n");
        sb.append("import org.springframework.web.filter.OncePerRequestFilter;\n\n");
        sb.append("import java.io.IOException;\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.time.Instant;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.concurrent.ConcurrentHashMap;\n\n");

        sb.append("/**\n");
        sb.append(" * Token-bucket API rate limiting filter powered by Bucket4j.\n");
        sb.append(" * Enforces per-client IP / authenticated principal request limits,\n");
        sb.append(" * returning HTTP 429 Too Many Requests and standard RFC rate-limit headers.\n");
        sb.append(" */\n");
        sb.append("@Component\n");
        sb.append("public class RateLimitingFilter extends OncePerRequestFilter {\n\n");

        sb.append("    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();\n\n");

        // Helper record/class for route limits
        sb.append("    private record RouteLimit(int capacity, Duration duration, String key) {}\n\n");

        sb.append("    @Override\n");
        sb.append("    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)\n");
        sb.append("            throws ServletException, IOException {\n\n");
        sb.append("        String uri = request.getRequestURI();\n");
        sb.append("        String method = request.getMethod();\n\n");

        sb.append("        RouteLimit limit = resolveRouteLimit(method, uri);\n");
        sb.append("        if (limit == null) {\n");
        sb.append("            filterChain.doFilter(request, response);\n");
        sb.append("            return;\n");
        sb.append("        }\n\n");

        sb.append("        String clientKey = resolveClientKey(request);\n");
        sb.append("        String bucketKey = limit.key() + \":\" + clientKey;\n");
        sb.append("        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(limit));\n\n");

        sb.append("        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);\n");
        sb.append("        if (probe.isConsumed()) {\n");
        sb.append("            response.setHeader(\"X-Rate-Limit-Limit\", String.valueOf(limit.capacity()));\n");
        sb.append("            response.setHeader(\"X-Rate-Limit-Remaining\", String.valueOf(probe.getRemainingTokens()));\n");
        sb.append("            filterChain.doFilter(request, response);\n");
        sb.append("        } else {\n");
        sb.append("            long waitSeconds = Math.max(1L, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));\n");
        sb.append("            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());\n");
        sb.append("            response.setHeader(\"Retry-After\", String.valueOf(waitSeconds));\n");
        sb.append("            response.setHeader(\"X-Rate-Limit-Limit\", String.valueOf(limit.capacity()));\n");
        sb.append("            response.setHeader(\"X-Rate-Limit-Remaining\", \"0\");\n");
        sb.append("            response.setHeader(\"X-Rate-Limit-Reset\", String.valueOf(waitSeconds));\n");
        sb.append("            response.setContentType(MediaType.APPLICATION_JSON_VALUE);\n\n");
        sb.append("            String json = \"\"\"{\n");
        sb.append("                \\\"timestamp\\\": \\\"\" + Instant.now() + \"\\\",\n");
        sb.append("                \\\"status\\\": 429,\n");
        sb.append("                \\\"error\\\": \\\"Too Many Requests\\\",\n");
        sb.append("                \\\"message\\\": \\\"Rate limit exceeded. Try again in \" + waitSeconds + \" seconds.\\\",\n");
        sb.append("                \\\"retryAfter\\\": \" + waitSeconds + \",\n");
        sb.append("                \\\"path\\\": \\\"\" + uri + \"\\\"\n");
        sb.append("            }\"\"\";\n");
        sb.append("            response.getWriter().write(json);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // createBucket method
        sb.append("    private Bucket createBucket(RouteLimit limit) {\n");
        sb.append("        Bandwidth bandwidth = Bandwidth.classic(limit.capacity(), Refill.greedy(limit.capacity(), limit.duration()));\n");
        sb.append("        return Bucket.builder().addLimit(bandwidth).build();\n");
        sb.append("    }\n\n");

        // resolveClientKey
        sb.append("    private String resolveClientKey(HttpServletRequest request) {\n");
        sb.append("        Authentication auth = SecurityContextHolder.getContext().getAuthentication();\n");
        sb.append("        if (auth != null && auth.isAuthenticated() && !\"anonymousUser\".equals(auth.getPrincipal())) {\n");
        sb.append("            return \"user:\" + auth.getName();\n");
        sb.append("        }\n");
        sb.append("        String xForwardedFor = request.getHeader(\"X-Forwarded-For\");\n");
        sb.append("        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {\n");
        sb.append("            return \"ip:\" + xForwardedFor.split(\",\")[0].trim();\n");
        sb.append("        }\n");
        sb.append("        String xRealIp = request.getHeader(\"X-Real-IP\");\n");
        sb.append("        if (xRealIp != null && !xRealIp.trim().isEmpty()) {\n");
        sb.append("            return \"ip:\" + xRealIp.trim();\n");
        sb.append("        }\n");
        sb.append("        return \"ip:\" + request.getRemoteAddr();\n");
        sb.append("    }\n\n");

        // resolveRouteLimit
        sb.append("    private RouteLimit resolveRouteLimit(String method, String uri) {\n");
        sb.append("        String m = method.toUpperCase();\n");

        // 1. Specific custom endpoints with rate limit
        for (ModelDef model : file.getModels()) {
            String modelPlural = TypeHelper.pluralize(model.getName().toLowerCase());
            for (CustomEndpointDef cep : model.getCustomEndpoints()) {
                if (cep.hasRateLimit()) {
                    RateLimitSpec spec = cep.getRateLimit();
                    String fullPath = basePath + "/" + modelPlural + (cep.getPath().startsWith("/") ? cep.getPath() : "/" + cep.getPath());
                    String regexPath = fullPath.replaceAll("\\{[^}]+\\}", "[^/]+");
                    sb.append("        if (\"").append(cep.getMethod()).append("\".equals(m) && uri.matches(\"")
                            .append(regexPath).append("\")) {\n");
                    sb.append("            return new RouteLimit(").append(spec.getCapacity())
                            .append(", Duration.ofSeconds(").append(spec.getDurationSeconds())
                            .append("), \"custom:").append(cep.getMethod()).append(":").append(fullPath).append("\");\n");
                    sb.append("        }\n");
                }
            }
        }

        // 2. Model CRUD rate limits
        for (ModelDef model : file.getModels()) {
            CrudConfig crud = model.getCrudConfig();
            if (crud != null && crud.hasRateLimit()) {
                RateLimitSpec spec = crud.getRateLimit();
                String modelPlural = TypeHelper.pluralize(model.getName().toLowerCase());
                String crudPrefix = basePath + "/" + modelPlural;
                sb.append("        if (uri.startsWith(\"").append(crudPrefix).append("\")) {\n");
                sb.append("            return new RouteLimit(").append(spec.getCapacity())
                        .append(", Duration.ofSeconds(").append(spec.getDurationSeconds())
                        .append("), \"crud:").append(model.getName()).append("\");\n");
                sb.append("        }\n");
            }
        }

        // 3. Global config rate limit fallback
        if (file.getConfig() != null && file.getConfig().hasRateLimit()) {
            RateLimitSpec globalSpec = file.getConfig().getRateLimit();
            sb.append("        if (uri.startsWith(\"").append(basePath).append("\")) {\n");
            sb.append("            return new RouteLimit(").append(globalSpec.getCapacity())
                    .append(", Duration.ofSeconds(").append(globalSpec.getDurationSeconds())
                    .append("), \"global:api\");\n");
            sb.append("        }\n");
        }

        sb.append("        return null;\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/security/RateLimitingFilter.java",
                sb.toString());
    }
}
