package dev.zenith.generator;

import dev.zenith.ast.FieldDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

public class SecurityGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        String basePkg = file.getConfig().getPackage();
        ModelDef userModel = file.getUserModel();
        if (userModel == null) return list;

        String userClassName = userModel.getName();
        String userRepoName = userClassName + "Repository";

        // Find email/username field
        FieldDef identityField = userModel.findField("email");
        if (identityField == null) identityField = userModel.findField("username");
        if (identityField == null) identityField = userModel.findField("name");
        String identityName = identityField != null ? identityField.getName() : "email";
        String identityCap = TypeHelper.capitalize(identityName);

        // Find password field
        FieldDef passwordField = null;
        for (FieldDef f : userModel.getFields()) {
            if (f.hasAnnotation("password") || f.getName().equalsIgnoreCase("password")) {
                passwordField = f;
                break;
            }
        }
        String passwordName = passwordField != null ? passwordField.getName() : "password";
        String passwordCap = TypeHelper.capitalize(passwordName);

        // Find role field
        FieldDef roleField = null;
        for (FieldDef f : userModel.getFields()) {
            if (f.hasAnnotation("role") || f.getName().equalsIgnoreCase("role")) {
                roleField = f;
                break;
            }
        }
        String roleName = roleField != null ? roleField.getName() : "role";
        String roleCap = TypeHelper.capitalize(roleName);

        // 1. Auth DTOs
        FieldDef userIdField = userModel.getIdField();
        String userIdType = userIdField != null ? TypeHelper.toSimpleJavaType(userIdField.getType()) : "Long";
        list.add(generateLoginRequest(basePkg));
        list.add(generateAuthResponse(basePkg, userIdType));

        // 2. JwtService
        list.add(generateJwtService(basePkg, file.getConfig().getJwtSecret(), file.getConfig().getJwtExpirationMs()));

        // 3. UserDetailsService
        list.add(generateUserDetailsService(basePkg, userClassName, userRepoName, identityName, identityCap, passwordCap, roleField, roleCap));

        // 4. JwtAuthFilter
        list.add(generateJwtAuthFilter(basePkg));

        // 5. SecurityConfig
        list.add(generateSecurityConfig(basePkg, file));

        // 6. AuthController
        list.add(generateAuthController(basePkg, userModel, identityName, identityCap, passwordCap, roleField, roleCap, file.getConfig().getBasePath()));

        return list;
    }

    private static JavaSourceFile generateLoginRequest(String basePkg) {
        String content = "package " + basePkg + ".dto;\n\n" +
                "import jakarta.validation.constraints.NotBlank;\n\n" +
                "public record LoginRequest(\n" +
                "    @NotBlank(message = \"Identity/Email cannot be blank\")\n" +
                "    String email,\n" +
                "    @NotBlank(message = \"Password cannot be blank\")\n" +
                "    String password\n" +
                ") {}\n";
        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/dto/LoginRequest.java", content);
    }

    private static JavaSourceFile generateAuthResponse(String basePkg, String userIdType) {
        String content = "package " + basePkg + ".dto;\n\n" +
                "import java.util.UUID;\n\n" +
                "public record AuthResponse(\n" +
                "    String token,\n" +
                "    String tokenType,\n" +
                "    " + userIdType + " userId,\n" +
                "    String email,\n" +
                "    String role\n" +
                ") {\n" +
                "    public static AuthResponse of(String token, " + userIdType + " userId, String email, String role) {\n" +
                "        return new AuthResponse(token, \"Bearer\", userId, email, role);\n" +
                "    }\n" +
                "}\n";
        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/dto/AuthResponse.java", content);
    }

    private static JavaSourceFile generateJwtService(String basePkg, String secret, long expirationMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".security;\n\n");
        sb.append("import io.jsonwebtoken.Claims;\n");
        sb.append("import io.jsonwebtoken.Jwts;\n");
        sb.append("import io.jsonwebtoken.security.Keys;\n");
        sb.append("import org.springframework.security.core.userdetails.UserDetails;\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import javax.crypto.SecretKey;\n");
        sb.append("import java.nio.charset.StandardCharsets;\n");
        sb.append("import java.util.Date;\n");
        sb.append("import java.util.HashMap;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.function.Function;\n\n");

        sb.append("@Service\n");
        sb.append("public class JwtService {\n\n");
        sb.append("    private static final String SECRET_STRING = \"").append(secret).append("\";\n");
        sb.append("    private static final long EXPIRATION_MS = ").append(expirationMs).append("L;\n\n");

        sb.append("    private SecretKey getSigningKey() {\n");
        sb.append("        return Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));\n");
        sb.append("    }\n\n");

        sb.append("    public String extractUsername(String token) {\n");
        sb.append("        return extractClaim(token, Claims::getSubject);\n");
        sb.append("    }\n\n");

        sb.append("    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {\n");
        sb.append("        final Claims claims = extractAllClaims(token);\n");
        sb.append("        return claimsResolver.apply(claims);\n");
        sb.append("    }\n\n");

        sb.append("    public String generateToken(UserDetails userDetails) {\n");
        sb.append("        return generateToken(new HashMap<>(), userDetails);\n");
        sb.append("    }\n\n");

        sb.append("    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {\n");
        sb.append("        return Jwts.builder()\n");
        sb.append("            .claims(extraClaims)\n");
        sb.append("            .subject(userDetails.getUsername())\n");
        sb.append("            .issuedAt(new Date(System.currentTimeMillis()))\n");
        sb.append("            .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))\n");
        sb.append("            .signWith(getSigningKey())\n");
        sb.append("            .compact();\n");
        sb.append("    }\n\n");

        sb.append("    public boolean isTokenValid(String token, UserDetails userDetails) {\n");
        sb.append("        final String username = extractUsername(token);\n");
        sb.append("        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);\n");
        sb.append("    }\n\n");

        sb.append("    private boolean isTokenExpired(String token) {\n");
        sb.append("        return extractClaim(token, Claims::getExpiration).before(new Date());\n");
        sb.append("    }\n\n");

        sb.append("    private Claims extractAllClaims(String token) {\n");
        sb.append("        return Jwts.parser()\n");
        sb.append("            .verifyWith(getSigningKey())\n");
        sb.append("            .build()\n");
        sb.append("            .parseSignedClaims(token)\n");
        sb.append("            .getPayload();\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/security/JwtService.java", sb.toString());
    }

    private static JavaSourceFile generateUserDetailsService(
            String basePkg, String userClass, String userRepo,
            String idField, String idCap, String passCap, FieldDef roleField, String roleCap
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".security;\n\n");
        sb.append("import ").append(basePkg).append(".entity.").append(userClass).append(";\n");
        sb.append("import ").append(basePkg).append(".repository.").append(userRepo).append(";\n");
        sb.append("import org.springframework.security.core.authority.SimpleGrantedAuthority;\n");
        sb.append("import org.springframework.security.core.userdetails.*;\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import java.util.Collections;\n");
        sb.append("import java.util.List;\n\n");

        sb.append("@Service\n");
        sb.append("public class CustomUserDetailsService implements UserDetailsService {\n\n");
        sb.append("    private final ").append(userRepo).append(" userRepository;\n\n");
        sb.append("    public CustomUserDetailsService(").append(userRepo).append(" userRepository) {\n");
        sb.append("        this.userRepository = userRepository;\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {\n");
        sb.append("        ").append(userClass).append(" user = userRepository.findBy").append(idCap).append("(username)\n");
        sb.append("            .orElseThrow(() -> new UsernameNotFoundException(\"User not found with identity: \" + username));\n\n");

        if (roleField != null) {
            sb.append("        String roleStr = user.get").append(roleCap).append("() != null ? user.get").append(roleCap).append("().toString() : \"USER\";\n");
            sb.append("        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(\"ROLE_\" + roleStr));\n");
        } else {
            sb.append("        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(\"ROLE_USER\"));\n");
        }

        sb.append("        return new org.springframework.security.core.userdetails.User(\n");
        sb.append("            user.get").append(idCap).append("(),\n");
        sb.append("            user.get").append(passCap).append("(),\n");
        sb.append("            authorities\n");
        sb.append("        );\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/security/CustomUserDetailsService.java", sb.toString());
    }

    private static JavaSourceFile generateJwtAuthFilter(String basePkg) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".security;\n\n");
        sb.append("import jakarta.servlet.FilterChain;\n");
        sb.append("import jakarta.servlet.ServletException;\n");
        sb.append("import jakarta.servlet.http.HttpServletRequest;\n");
        sb.append("import jakarta.servlet.http.HttpServletResponse;\n");
        sb.append("import org.springframework.lang.NonNull;\n");
        sb.append("import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;\n");
        sb.append("import org.springframework.security.core.context.SecurityContextHolder;\n");
        sb.append("import org.springframework.security.core.userdetails.UserDetails;\n");
        sb.append("import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;\n");
        sb.append("import org.springframework.stereotype.Component;\n");
        sb.append("import org.springframework.web.filter.OncePerRequestFilter;\n");
        sb.append("import java.io.IOException;\n\n");

        sb.append("@Component\n");
        sb.append("public class JwtAuthenticationFilter extends OncePerRequestFilter {\n\n");
        sb.append("    private final JwtService jwtService;\n");
        sb.append("    private final CustomUserDetailsService userDetailsService;\n\n");

        sb.append("    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {\n");
        sb.append("        this.jwtService = jwtService;\n");
        sb.append("        this.userDetailsService = userDetailsService;\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    protected void doFilterInternal(\n");
        sb.append("        @NonNull HttpServletRequest request,\n");
        sb.append("        @NonNull HttpServletResponse response,\n");
        sb.append("        @NonNull FilterChain filterChain\n");
        sb.append("    ) throws ServletException, IOException {\n");
        sb.append("        final String authHeader = request.getHeader(\"Authorization\");\n");
        sb.append("        final String jwt;\n");
        sb.append("        final String userEmail;\n\n");

        sb.append("        if (authHeader == null || !authHeader.startsWith(\"Bearer \")) {\n");
        sb.append("            filterChain.doFilter(request, response);\n");
        sb.append("            return;\n");
        sb.append("        }\n\n");

        sb.append("        jwt = authHeader.substring(7);\n");
        sb.append("        userEmail = jwtService.extractUsername(jwt);\n\n");

        sb.append("        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {\n");
        sb.append("            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);\n");
        sb.append("            if (jwtService.isTokenValid(jwt, userDetails)) {\n");
        sb.append("                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(\n");
        sb.append("                    userDetails,\n");
        sb.append("                    null,\n");
        sb.append("                    userDetails.getAuthorities()\n");
        sb.append("                );\n");
        sb.append("                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));\n");
        sb.append("                SecurityContextHolder.getContext().setAuthentication(authToken);\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        filterChain.doFilter(request, response);\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/security/JwtAuthenticationFilter.java", sb.toString());
    }

    private static JavaSourceFile generateSecurityConfig(String basePkg, ZenithFile file) {
        String basePath = file.getConfig().getBasePath();
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".config;\n\n");
        sb.append("import ").append(basePkg).append(".security.JwtAuthenticationFilter;\n");
        sb.append("import org.springframework.context.annotation.Bean;\n");
        sb.append("import org.springframework.context.annotation.Configuration;\n");
        sb.append("import org.springframework.http.HttpMethod;\n");
        sb.append("import org.springframework.security.authentication.AuthenticationManager;\n");
        sb.append("import org.springframework.security.config.Customizer;\n");
        sb.append("import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;\n");
        sb.append("import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;\n");
        sb.append("import org.springframework.security.config.annotation.web.builders.HttpSecurity;\n");
        sb.append("import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;\n");
        sb.append("import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;\n");
        sb.append("import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;\n");
        sb.append("import org.springframework.security.config.http.SessionCreationPolicy;\n");
        sb.append("import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;\n");
        sb.append("import org.springframework.security.crypto.password.PasswordEncoder;\n");
        sb.append("import org.springframework.security.web.SecurityFilterChain;\n");
        sb.append("import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;\n\n");

        sb.append("@Configuration\n");
        sb.append("@EnableWebSecurity\n");
        sb.append("@EnableMethodSecurity\n");
        sb.append("public class SecurityConfig {\n\n");

        sb.append("    private final JwtAuthenticationFilter jwtAuthFilter;\n\n");
        sb.append("    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {\n");
        sb.append("        this.jwtAuthFilter = jwtAuthFilter;\n");
        sb.append("    }\n\n");

        sb.append("    @Bean\n");
        sb.append("    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {\n");
        sb.append("        http\n");
        sb.append("            .csrf(AbstractHttpConfigurer::disable)\n");
        sb.append("            .cors(Customizer.withDefaults())\n");
        sb.append("            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))\n");
        sb.append("            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))\n");
        sb.append("            .authorizeHttpRequests(auth -> auth\n");
        sb.append("                .requestMatchers(\"").append(basePath).append("/auth/**\").permitAll()\n");
        sb.append("                .requestMatchers(\"/swagger-ui/**\", \"/v3/api-docs/**\", \"/swagger-ui.html\").permitAll()\n");
        sb.append("                .requestMatchers(\"/h2-console/**\").permitAll()\n");
        sb.append("                .anyRequest().permitAll()\n");
        sb.append("            )\n");
        sb.append("            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);\n\n");
        sb.append("        return http.build();\n");
        sb.append("    }\n\n");

        sb.append("    @Bean\n");
        sb.append("    public PasswordEncoder passwordEncoder() {\n");
        sb.append("        return new BCryptPasswordEncoder();\n");
        sb.append("    }\n\n");

        sb.append("    @Bean\n");
        sb.append("    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {\n");
        sb.append("        return config.getAuthenticationManager();\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/config/SecurityConfig.java", sb.toString());
    }

    private static JavaSourceFile generateAuthController(
            String basePkg, ModelDef userModel,
            String idField, String idCap, String passCap, FieldDef roleField, String roleCap, String basePath
    ) {
        String userClass = userModel.getName();
        String userRepo = userClass + "Repository";
        String mapperName = userClass + "Mapper";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".controller;\n\n");
        sb.append("import ").append(basePkg).append(".dto.*;\n");
        sb.append("import ").append(basePkg).append(".entity.").append(userClass).append(";\n");
        sb.append("import ").append(basePkg).append(".mapper.").append(mapperName).append(";\n");
        sb.append("import ").append(basePkg).append(".repository.").append(userRepo).append(";\n");
        sb.append("import ").append(basePkg).append(".security.JwtService;\n");
        sb.append("import io.swagger.v3.oas.annotations.Operation;\n");
        sb.append("import io.swagger.v3.oas.annotations.tags.Tag;\n");
        sb.append("import jakarta.validation.Valid;\n");
        sb.append("import org.springframework.http.HttpStatus;\n");
        sb.append("import org.springframework.http.ResponseEntity;\n");
        sb.append("import org.springframework.security.authentication.AuthenticationManager;\n");
        sb.append("import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;\n");
        sb.append("import org.springframework.security.core.userdetails.UserDetails;\n");
        sb.append("import org.springframework.security.core.userdetails.UserDetailsService;\n");
        sb.append("import org.springframework.security.crypto.password.PasswordEncoder;\n");
        sb.append("import org.springframework.web.bind.annotation.*;\n\n");

        sb.append("@RestController\n");
        sb.append("@RequestMapping(\"").append(basePath).append("/auth\")\n");
        sb.append("@CrossOrigin\n");
        sb.append("@Tag(name = \"Authentication\", description = \"Endpoints for user login and registration\")\n");
        sb.append("public class AuthController {\n\n");

        sb.append("    private final AuthenticationManager authManager;\n");
        sb.append("    private final JwtService jwtService;\n");
        sb.append("    private final UserDetailsService userDetailsService;\n");
        sb.append("    private final ").append(userRepo).append(" userRepository;\n");
        sb.append("    private final ").append(mapperName).append(" mapper;\n");
        sb.append("    private final PasswordEncoder passwordEncoder;\n\n");

        sb.append("    public AuthController(\n");
        sb.append("        AuthenticationManager authManager,\n");
        sb.append("        JwtService jwtService,\n");
        sb.append("        UserDetailsService userDetailsService,\n");
        sb.append("        ").append(userRepo).append(" userRepository,\n");
        sb.append("        ").append(mapperName).append(" mapper,\n");
        sb.append("        PasswordEncoder passwordEncoder\n");
        sb.append("    ) {\n");
        sb.append("        this.authManager = authManager;\n");
        sb.append("        this.jwtService = jwtService;\n");
        sb.append("        this.userDetailsService = userDetailsService;\n");
        sb.append("        this.userRepository = userRepository;\n");
        sb.append("        this.mapper = mapper;\n");
        sb.append("        this.passwordEncoder = passwordEncoder;\n");
        sb.append("    }\n\n");

        // Login endpoint
        sb.append("    @PostMapping(\"/login\")\n");
        sb.append("    @Operation(summary = \"Authenticate user and return JWT token\")\n");
        sb.append("    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {\n");
        sb.append("        authManager.authenticate(\n");
        sb.append("            new UsernamePasswordAuthenticationToken(request.email(), request.password())\n");
        sb.append("        );\n");
        sb.append("        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());\n");
        sb.append("        String token = jwtService.generateToken(userDetails);\n");
        sb.append("        ").append(userClass).append(" entity = userRepository.findBy").append(idCap).append("(request.email()).orElseThrow();\n");
        if (roleField != null) {
            sb.append("        String role = entity.get").append(roleCap).append("() != null ? entity.get").append(roleCap).append("().toString() : \"USER\";\n");
        } else {
            sb.append("        String role = \"USER\";\n");
        }
        sb.append("        return ResponseEntity.ok(AuthResponse.of(token, entity.getId(), entity.get").append(idCap).append("(), role));\n");
        sb.append("    }\n\n");

        // Register endpoint
        sb.append("    @PostMapping(\"/register\")\n");
        sb.append("    @Operation(summary = \"Register new user account\")\n");
        sb.append("    public ResponseEntity<AuthResponse> register(@Valid @RequestBody Create").append(userClass).append("Request request) {\n");
        sb.append("        ").append(userClass).append(" entity = mapper.toEntity(request);\n");
        sb.append("        entity.set").append(passCap).append("(passwordEncoder.encode(request.password()));\n");
        sb.append("        ").append(userClass).append(" saved = userRepository.save(entity);\n");
        sb.append("        UserDetails userDetails = userDetailsService.loadUserByUsername(saved.get").append(idCap).append("());\n");
        sb.append("        String token = jwtService.generateToken(userDetails);\n");
        if (roleField != null) {
            sb.append("        String role = saved.get").append(roleCap).append("() != null ? saved.get").append(roleCap).append("().toString() : \"USER\";\n");
        } else {
            sb.append("        String role = \"USER\";\n");
        }
        sb.append("        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.of(token, saved.getId(), saved.get").append(idCap).append("(), role));\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile("src/main/java/" + basePkg.replace('.', '/') + "/controller/AuthController.java", sb.toString());
    }
}
