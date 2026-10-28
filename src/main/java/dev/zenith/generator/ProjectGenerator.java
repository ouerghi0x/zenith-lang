package dev.zenith.generator;

import dev.zenith.ast.EnumDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;

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

        // 5. Enums
        for (EnumDef e : file.getEnums()) {
            files.add(EnumGenerator.generate(e, file));
        }

        // 6. Shared DTOs
        files.add(DtoGenerator.generatePageResponse(file));

        // 7. Exception handling
        files.addAll(ExceptionHandlerGenerator.generate(file));

        // 8. Per-model components (Entity, DTOs, Repository, Mapper, Service, Controller)
        for (ModelDef model : file.getModels()) {
            files.add(EntityGenerator.generate(model, file));
            files.addAll(DtoGenerator.generate(model, file));
            files.add(RepositoryGenerator.generate(model, file));
            files.add(MapperGenerator.generate(model, file));
            files.addAll(ServiceGenerator.generate(model, file));
            files.add(ControllerGenerator.generate(model, file));
        }

        // 9. Phase 5: Full-Stack TypeScript / React Query SDK
        files.addAll(TypeScriptSdkGenerator.generate(file));

        // 10. Phase 5: Containerization (Dockerfile & docker-compose.yml)
        files.addAll(DockerGenerator.generate(file));

        // 11. Project README.md with generated API documentation
        files.add(generateReadme(file));

        return files;
    }

    public static void writeToDirectory(ZenithFile file, Path outputDir) throws IOException {
        List<JavaSourceFile> sourceFiles = generateAll(file);
        for (JavaSourceFile src : sourceFiles) {
            Path targetPath = outputDir.resolve(src.getRelativePath());
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, src.getContent(), StandardCharsets.UTF_8);
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
            sb.append("- **Swagger UI**: `http://localhost:").append(file.getConfig().getPort()).append("/swagger-ui.html`\n");
            sb.append("- **OpenAPI JSON Spec**: `http://localhost:").append(file.getConfig().getPort()).append("/v3/api-docs`\n\n");
        }

        if (file.hasAuth()) {
            String authBase = file.getConfig().getBasePath() + "/auth";
            sb.append("### 3. Authentication & JWT Endpoints\n\n");
            sb.append("| Method | Endpoint | Description |\n");
            sb.append("| :--- | :--- | :--- |\n");
            sb.append("| `POST` | `").append(authBase).append("/login` | Authenticate identity/password and receive JWT Bearer token |\n");
            sb.append("| `POST` | `").append(authBase).append("/register` | Register new account and receive JWT Bearer token |\n\n");
            sb.append("Use the token in the `Authorization: Bearer <token>` header for protected endpoints.\n\n");
        }

        sb.append("### 4. Available Resource Endpoints\n\n");
        for (ModelDef model : file.getModels()) {
            String basePath = file.getConfig().getBasePath() + "/" + TypeHelper.toSnakeCase(TypeHelper.pluralize(model.getName()));
            sb.append("#### ").append(model.getName()).append(" Resource (`").append(basePath).append("`)\n\n");
            sb.append("| Method | Endpoint | Description |\n");
            sb.append("| :--- | :--- | :--- |\n");
            sb.append("| `GET` | `").append(basePath).append("` | List ").append(model.getName()).append("s (supports `?page=0&size=20&sort=id&direction=asc&search=...`) |\n");
            sb.append("| `GET` | `").append(basePath).append("/{id}` | Fetch ").append(model.getName()).append(" by ID |\n");
            sb.append("| `POST` | `").append(basePath).append("` | Create new ").append(model.getName()).append(" |\n");
            sb.append("| `PUT` | `").append(basePath).append("/{id}` | Update existing ").append(model.getName()).append(" |\n");
            sb.append("| `DELETE` | `").append(basePath).append("/{id}` | Delete ").append(model.getName()).append(" |\n");

            for (var custom : model.getCustomEndpoints()) {
                sb.append("| `").append(custom.getMethod()).append("` | `").append(basePath).append(custom.getPath()).append("` | ").append(custom.getDescription().isEmpty() ? "Custom action" : custom.getDescription()).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("### 5. Database, Migrations & H2 Console\n\n");
        if (file.getConfig().isFlywayEnabled()) {
            sb.append("- **Flyway Migration DDL**: Located at `src/main/resources/db/migration/V1__initial_schema.sql`\n");
        }
        sb.append("- **In-memory H2 DB Console**: `http://localhost:").append(file.getConfig().getPort()).append("/h2-console`\n");
        sb.append("- **JDBC URL**: `jdbc:h2:mem:appdb`\n");
        sb.append("- **User**: `sa` | **Password**: (empty)\n");

        return new JavaSourceFile("README.md", sb.toString());
    }
}
