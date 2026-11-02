package dev.zenith.generator;

import dev.zenith.ast.ZenithFile;

public class PropertiesGenerator {

    public static JavaSourceFile generate(ZenithFile file) {
        int port = file.getConfig().getPort();
        String db = file.getConfig().getDatabase();
        boolean flyway = file.getConfig().isFlywayEnabled();
        boolean swagger = file.getConfig().isSwaggerEnabled();
        boolean hasAuth = file.hasAuth();

        StringBuilder sb = new StringBuilder();
        sb.append("server:\n");
        sb.append("  port: ").append(port).append("\n\n");

        sb.append("spring:\n");
        sb.append("  datasource:\n");
        if ("postgres".equalsIgnoreCase(db)) {
            sb.append("    url: jdbc:postgresql://localhost:5432/appdb\n");
            sb.append("    username: postgres\n");
            sb.append("    password: postgres\n");
            sb.append("    driver-class-name: org.postgresql.Driver\n");
        } else {
            sb.append("    url: jdbc:h2:mem:appdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=KEY,VALUE\n");
            sb.append("    driver-class-name: org.h2.Driver\n");
            sb.append("    username: sa\n");
            sb.append("    password: \"\"\n");
        }

        sb.append("  h2:\n");
        sb.append("    console:\n");
        sb.append("      enabled: true\n");
        sb.append("      path: /h2-console\n");

        if (flyway) {
            sb.append("  flyway:\n");
            sb.append("    enabled: true\n");
            sb.append("    baseline-on-migrate: true\n");
        }

        sb.append("  jpa:\n");
        sb.append("    hibernate:\n");
        if (flyway) {
            sb.append("      ddl-auto: validate\n");
        } else {
            sb.append("      ddl-auto: update\n");
        }
        sb.append("    show-sql: true\n");
        sb.append("    properties:\n");
        sb.append("      hibernate:\n");
        sb.append("        format_sql: true\n\n");

        if (swagger) {
            sb.append("springdoc:\n");
            sb.append("  swagger-ui:\n");
            sb.append("    path: /swagger-ui.html\n");
            sb.append("    operationsSorter: method\n");
            sb.append("  api-docs:\n");
            sb.append("    path: /v3/api-docs\n\n");
        }

        if (hasAuth) {
            sb.append("jwt:\n");
            sb.append("  secret: \"").append(file.getConfig().getJwtSecret()).append("\"\n");
            sb.append("  expiration: ").append(file.getConfig().getJwtExpirationMs()).append("\n\n");
        }

        if (file.hasStorage()) {
            String defProvider = file.getConfig().getStorageProvider();
            String defBucket = file.getConfig().getStorageBucket();
            sb.append("zenith:\n");
            sb.append("  storage:\n");
            sb.append("    provider: ${STORAGE_PROVIDER:").append(defProvider).append("}\n");
            sb.append("    bucket: ${STORAGE_BUCKET:").append(defBucket).append("}\n");
            sb.append("    minio:\n");
            sb.append("      endpoint: ${STORAGE_ENDPOINT:http://localhost:9000}\n");
            sb.append("      access-key: ${STORAGE_ACCESS_KEY:minioadmin}\n");
            sb.append("      secret-key: ${STORAGE_SECRET_KEY:minioadmin}\n");
            sb.append("    s3:\n");
            sb.append("      region: ${STORAGE_REGION:us-east-1}\n");
            sb.append("      access-key: ${STORAGE_ACCESS_KEY:}\n");
            sb.append("      secret-key: ${STORAGE_SECRET_KEY:}\n");
        }

        return new JavaSourceFile("src/main/resources/application.yml", sb.toString());
    }
}
