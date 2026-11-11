package dev.zenith.generator;

import dev.zenith.ast.DtoDef;
import dev.zenith.ast.EnumDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.generator.trace.GenerationManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProjectGenerator {

    public static List<JavaSourceFile> generateAll(ZenithFile file) {
        List<JavaSourceFile> files = new ArrayList<>();

        // 1. Build & Config files
        files.add(PomGenerator.generate(file));
        files.add(PropertiesGenerator.generate(file));
        files.add(ApplicationGenerator.generate(file));

        // 2. Swagger / OpenAPI 3
        if (file.getConfig().isSwaggerEnabled()) {
            files.add(OpenApiGenerator.generate(file));
        }

        // 3. Flyway Database Migration
        if (file.getConfig().isFlywayEnabled()) {
            files.add(FlywayGenerator.generate(file));
        }

        // 4. Spring Security 6 & JWT Auth
        if (file.hasAuth()) {
            files.addAll(SecurityGenerator.generate(file));
        }

        // 4.2. API Rate Limiting (Bucket4j)
        if (file.hasRateLimiting()) {
            files.addAll(RateLimitGenerator.generate(file));
        }

        // 4.5. Object Storage & Media Management (MinIO / AWS S3)
        if (file.hasStorage()) {
            files.addAll(StorageGenerator.generate(file));
        }

        // 4.8. Transactional Email Service (JavaMailSender / Thymeleaf)
        if (file.hasMails()) {
            files.addAll(EmailGenerator.generate(file));
        }

        // 4.9. Caching Layer (Redis / Caffeine)
        if (file.hasCache()) {
            files.add(CacheConfigGenerator.generate(file));
        }

        // 4.10. Real-Time WebSockets & STOMP Broker (Spec #18)
        if (file.hasSockets()) {
            files.addAll(WebSocketGenerator.generate(file));
        }

        // 5. Enums
        for (EnumDef e : file.getEnums()) {
            files.add(EnumGenerator.generate(e, file));
        }
        // dto
        for (DtoDef dto : file.getDtos()) {
            files.addAll(DtoGenerator.generateJustDto(dto, file));
        }

        // 6. Shared DTOs
        files.add(DtoGenerator.generatePageResponse(file));

        // 7. Exception handling
        files.addAll(ExceptionHandlerGenerator.generate(file));

        // 8. Per-model components (Entity, DTOs, Repository, Mapper, Service,
        // Controller)
        ModelDef userModel = file.getUserModel();
        for (ModelDef model : file.getModels()) {
            files.add(EntityGenerator.generate(model, file));
            files.add(RepositoryGenerator.generate(model, file));

            boolean isAuthUser = file.hasAuth() && userModel != null && userModel.getName().equals(model.getName());

            if (model.hasEndpoints()) {
                files.addAll(DtoGenerator.generate(model, file));
                files.add(MapperGenerator.generate(model, file));
                files.addAll(ServiceGenerator.generate(model, file));
                files.add(ControllerGenerator.generate(model, file));
            } else if (isAuthUser) {
                // Auth user model needs DTOs and Mapper for AuthController (/auth/register),
                // but no REST Controller or Service!
                files.addAll(DtoGenerator.generate(model, file));
                files.add(MapperGenerator.generate(model, file));
            }
        }
        // 9. Seeds
        if (!file.getSeeds().isEmpty()) {
            files.add(SeedGenerator.generate(file));
        }

        // 9.5 Background Scheduled Jobs & Workers
        if (file.hasJobs()) {
            files.addAll(JobGenerator.generate(file));
        }

        // 10. Phase 5: Full-Stack TypeScript / React Query SDK
        files.addAll(TypeScriptSdkGenerator.generate(file));

        // 10. Phase 5: Containerization (Dockerfile & docker-compose.yml)
        files.addAll(DockerGenerator.generate(file));

        // 11. Phase 6: Automated Integration & Unit Tests Generation
        files.addAll(TestGenerator.generate(file));

        // 12. Project README.md with generated API documentation
        files.add(generateReadme(file));

        return files;
    }

    public static GenerationManifest buildManifest(ZenithFile file, List<JavaSourceFile> sourceFiles) {
        String rootSchema = "schema.zen";
        if (file.getModels() != null && !file.getModels().isEmpty() && file.getModels().get(0).getSourceFile() != null) {
            rootSchema = file.getModels().get(0).getSourceFile();
        }
        GenerationManifest manifest = new GenerationManifest(rootSchema);
        for (JavaSourceFile src : sourceFiles) {
            manifest.addAllMappings(src.getMappings());
        }
        return manifest;
    }

    public static void writeToDirectory(ZenithFile file, Path outputDir) throws IOException {
        List<JavaSourceFile> sourceFiles = generateAll(file);
        java.util.Set<Path> generatedPaths = new java.util.HashSet<>();
        for (JavaSourceFile src : sourceFiles) {
            Path targetPath = outputDir.resolve(src.getRelativePath());
            generatedPaths.add(targetPath.toAbsolutePath().normalize());
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, src.getContent(), StandardCharsets.UTF_8);
        }

        // Emit Generation Source Map Manifest (.zenith/generation-manifest.json)
        GenerationManifest manifest = buildManifest(file, sourceFiles);
        Path manifestPath = outputDir.resolve(".zenith/generation-manifest.json");
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, manifest.toJson(), StandardCharsets.UTF_8);
        generatedPaths.add(manifestPath.toAbsolutePath().normalize());

        // Clean up orphaned .java files from previous builds
        String basePkg = file.getConfig().getPackage();
        Path javaSrcDir = outputDir.resolve("src/main/java/" + basePkg.replace('.', '/'));
        if (Files.exists(javaSrcDir)) {
            try (var stream = Files.walk(javaSrcDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !generatedPaths.contains(p.toAbsolutePath().normalize()))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        Path javaTestDir = outputDir.resolve("src/test/java/" + basePkg.replace('.', '/'));
        if (Files.exists(javaTestDir)) {
            try (var stream = Files.walk(javaTestDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !generatedPaths.contains(p.toAbsolutePath().normalize()))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }

    private static JavaSourceFile generateReadme(ZenithFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(file.getConfig().getApp()).append("\n\n");
        sb.append("> Generated automatically by **Zenith DSL** on top of Spring Boot 3 & Java 17.\n\n");

        sb.append("## Quick Start\n\n");
        sb.append("### 1. Build & Run\n");
        sb.append("```bash\n");
        sb.append("mvn spring-boot:run\n");
        sb.append("```\n\n");
        sb.append("Server will start on port `").append(file.getConfig().getPort()).append("`.\n\n");

        if (file.getConfig().isSwaggerEnabled()) {
            sb.append("### 2. Interactive Swagger UI (OpenAPI 3)\n\n");
            sb.append("- **Swagger UI**: `http://localhost:").append(file.getConfig().getPort())
                    .append("/swagger-ui.html`\n");
            sb.append("- **OpenAPI JSON Spec**: `http://localhost:").append(file.getConfig().getPort())
                    .append("/v3/api-docs`\n\n");
        }

        if (file.hasAuth()) {
            String authBase = file.getConfig().getBasePath() + "/auth";
            sb.append("### 3. Authentication & JWT Endpoints\n\n");
            sb.append("| Method | Endpoint | Description |\n");
            sb.append("| :--- | :--- | :--- |\n");
            sb.append("| `POST` | `").append(authBase)
                    .append("/login` | Authenticate identity/password and receive JWT Bearer token |\n");
            sb.append("| `POST` | `").append(authBase)
                    .append("/register` | Register new account and receive JWT Bearer token |\n\n");
            sb.append("Use the token in the `Authorization: Bearer <token>` header for protected endpoints.\n\n");
        }

        sb.append("### 4. Available Resource Endpoints\n\n");
        for (ModelDef model : file.getModels()) {
            if (!model.hasEndpoints()) {
                continue;
            }
            String basePath = file.getConfig().getBasePath() + "/"
                    + TypeHelper.toSnakeCase(TypeHelper.pluralize(model.getName()));
            sb.append("#### ").append(model.getName()).append(" Resource (`").append(basePath).append("`)\n\n");
            sb.append("| Method | Endpoint | Description |\n");
            sb.append("| :--- | :--- | :--- |\n");

            if (model.getCrudConfig() != null) {
                sb.append("| `GET` | `").append(basePath).append("` | List ").append(model.getName())
                        .append("s (supports `?page=0&size=20&sort=id&direction=asc&search=...`) |\n");
                sb.append("| `GET` | `").append(basePath).append("/{id}` | Fetch ").append(model.getName())
                        .append(" by ID |\n");
                sb.append("| `POST` | `").append(basePath).append("` | Create new ").append(model.getName())
                        .append(" |\n");
                sb.append("| `PUT` | `").append(basePath).append("/{id}` | Update existing ").append(model.getName())
                        .append(" |\n");
                sb.append("| `DELETE` | `").append(basePath).append("/{id}` | Delete ").append(model.getName())
                        .append(" |\n");
            }

            for (var custom : model.getCustomEndpoints()) {
                sb.append("| `").append(custom.getMethod()).append("` | `").append(basePath).append(custom.getPath())
                        .append("` | ")
                        .append(custom.getDescription().isEmpty() ? "Custom action" : custom.getDescription())
                        .append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("### 5. Database, Migrations & H2 Console\n\n");
        if (file.getConfig().isFlywayEnabled()) {
            sb.append(
                    "- **Flyway Migration DDL**: Located at `src/main/resources/db/migration/V1__initial_schema.sql`\n");
        }
        sb.append("- **In-memory H2 DB Console**: `http://localhost:").append(file.getConfig().getPort())
                .append("/h2-console`\n");
        sb.append("- **JDBC URL**: `jdbc:h2:mem:appdb`\n");
        sb.append("- **User**: `sa` | **Password**: (empty)\n\n");

        sb.append("### 6. Automated Unit & Integration Tests\n\n");
        sb.append("Zenith auto-generates comprehensive JUnit 5 & MockMvc integration tests for controllers, security rules, RBAC guards, and repositories:\n\n");
        sb.append("```bash\n");
        sb.append("mvn test\n");
        sb.append("```\n");
        sb.append("Tests run completely isolated against an in-memory H2 database using `src/test/resources/application.yml` without requiring external infrastructure.\n");

        return new JavaSourceFile("README.md", sb.toString());
    }
}
