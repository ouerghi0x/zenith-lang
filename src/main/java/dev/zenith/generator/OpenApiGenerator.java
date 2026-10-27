package dev.zenith.generator;

import dev.zenith.ast.ZenithFile;

public class OpenApiGenerator {

    public static JavaSourceFile generate(ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String appName = file.getConfig().getApp();
        boolean hasAuth = file.hasAuth();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".config;\n\n");

        sb.append("import io.swagger.v3.oas.annotations.OpenAPIDefinition;\n");
        sb.append("import io.swagger.v3.oas.annotations.info.Info;\n");
        sb.append("import io.swagger.v3.oas.annotations.info.Contact;\n");
        if (hasAuth) {
            sb.append("import io.swagger.v3.oas.annotations.security.SecurityRequirement;\n");
            sb.append("import io.swagger.v3.oas.annotations.security.SecurityScheme;\n");
            sb.append("import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;\n");
        }
        sb.append("import org.springframework.context.annotation.Configuration;\n\n");

        sb.append("@Configuration\n");
        sb.append("@OpenAPIDefinition(\n");
        sb.append("    info = @Info(\n");
        sb.append("        title = \"").append(appName).append(" API\",\n");
        sb.append("        version = \"1.0.0\",\n");
        sb.append("        description = \"Enterprise REST API generated automatically by Zenith DSL on top of Spring Boot 3.\"\n");
        sb.append("    )");
        if (hasAuth) {
            sb.append(",\n    security = @SecurityRequirement(name = \"BearerAuth\")\n");
        } else {
            sb.append("\n");
        }
        sb.append(")\n");

        if (hasAuth) {
            sb.append("@SecurityScheme(\n");
            sb.append("    name = \"BearerAuth\",\n");
            sb.append("    type = SecuritySchemeType.HTTP,\n");
            sb.append("    scheme = \"bearer\",\n");
            sb.append("    bearerFormat = \"JWT\",\n");
            sb.append("    description = \"Enter your JWT Bearer token to authorize requests\"\n");
            sb.append(")\n");
        }

        sb.append("public class OpenApiConfig {\n");
        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/config/OpenApiConfig.java";
        return new JavaSourceFile(path, sb.toString());
    }
}
