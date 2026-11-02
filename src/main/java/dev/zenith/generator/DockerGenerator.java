package dev.zenith.generator;

import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates ready-to-run Docker and Docker Compose environments:
 * - Multi-stage Dockerfile (Maven 3 + Eclipse Temurin 17 JRE slim)
 * - docker-compose.yml pairing the Spring Boot API with PostgreSQL/MySQL/H2
 * - .dockerignore for clean builds
 */
public class DockerGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        list.add(generateDockerfile(file));
        list.add(generateDockerCompose(file));
        list.add(generateDockerignore());
        return list;
    }

    private static JavaSourceFile generateDockerfile(ZenithFile file) {
        int port = file.getConfig().getPort();
        String content = "# -----------------------------------------------------------------------------\n" +
                "# Multi-Stage Dockerfile for " + file.getConfig().getApp() + "\n" +
                "# Generated automatically by Zenith DSL v1.0.0\n" +
                "# -----------------------------------------------------------------------------\n\n" +
                "# 1. Build Stage\n" +
                "FROM maven:3.9-eclipse-temurin-17 AS builder\n" +
                "WORKDIR /build\n" +
                "COPY pom.xml .\n" +
                "RUN mvn dependency:go-offline -B\n" +
                "COPY src ./src\n" +
                "RUN mvn clean package -DskipTests\n\n" +
                "# 2. Runtime Stage\n" +
                "FROM eclipse-temurin:17-jre-alpine\n" +
                "WORKDIR /app\n" +
                "RUN addgroup -S zenith && adduser -S zenith -G zenith\n" +
                "USER zenith:zenith\n" +
                "COPY --from=builder /build/target/*.jar app.jar\n" +
                "EXPOSE " + port + "\n" +
                "ENV SPRING_PROFILES_ACTIVE=prod\n" +
                "ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n";

        return new JavaSourceFile("Dockerfile", content);
    }

    private static JavaSourceFile generateDockerCompose(ZenithFile file) {
        int port = file.getConfig().getPort();
        String db = file.getConfig().getDatabase().toLowerCase();
        String appName = file.getConfig().getApp().toLowerCase().replaceAll("[^a-z0-9]", "");

        StringBuilder sb = new StringBuilder();
        sb.append("version: '3.8'\n\n");
        sb.append("services:\n");
        sb.append("  api:\n");
        sb.append("    build:\n");
        sb.append("      context: .\n");
        sb.append("      dockerfile: Dockerfile\n");
        sb.append("    ports:\n");
        sb.append("      - \"").append(port).append(":").append(port).append("\"\n");
        sb.append("    environment:\n");
        sb.append("      SERVER_PORT: ").append(port).append("\n");

        if (file.hasStorage()) {
            sb.append("      STORAGE_PROVIDER: minio\n");
            sb.append("      STORAGE_ENDPOINT: http://minio:9000\n");
            sb.append("      STORAGE_BUCKET: ").append(file.getConfig().getStorageBucket()).append("\n");
            sb.append("      STORAGE_ACCESS_KEY: minioadmin\n");
            sb.append("      STORAGE_SECRET_KEY: minioadmin\n");
        }

        if ("postgres".equals(db) || "postgresql".equals(db)) {
            sb.append("      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/").append(appName).append("_db\n");
            sb.append("      SPRING_DATASOURCE_USERNAME: zenith\n");
            sb.append("      SPRING_DATASOURCE_PASSWORD: zenith_secret\n");
            sb.append("    depends_on:\n");
            sb.append("      db:\n");
            sb.append("        condition: service_healthy\n\n");
            sb.append("  db:\n");
            sb.append("    image: postgres:15-alpine\n");
            sb.append("    environment:\n");
            sb.append("      POSTGRES_DB: ").append(appName).append("_db\n");
            sb.append("      POSTGRES_USER: zenith\n");
            sb.append("      POSTGRES_PASSWORD: zenith_secret\n");
            sb.append("    ports:\n");
            sb.append("      - \"5432:5432\"\n");
            sb.append("    volumes:\n");
            sb.append("      - pgdata:/var/lib/postgresql/data\n");
            sb.append("    healthcheck:\n");
            sb.append("      test: [\"CMD-SHELL\", \"pg_isready -U zenith -d ").append(appName).append("_db\"]\n");
            sb.append("      interval: 5s\n");
            sb.append("      timeout: 5s\n");
            sb.append("      retries: 5\n\n");
        } else if ("mysql".equals(db)) {
            sb.append("      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/").append(appName)
                    .append("_db?useSSL=false&allowPublicKeyRetrieval=true\n");
            sb.append("      SPRING_DATASOURCE_USERNAME: zenith\n");
            sb.append("      SPRING_DATASOURCE_PASSWORD: zenith_secret\n");
            sb.append("    depends_on:\n");
            sb.append("      - db\n\n");
            sb.append("  db:\n");
            sb.append("    image: mysql:8.0\n");
            sb.append("    environment:\n");
            sb.append("      MYSQL_DATABASE: ").append(appName).append("_db\n");
            sb.append("      MYSQL_USER: zenith\n");
            sb.append("      MYSQL_PASSWORD: zenith_secret\n");
            sb.append("      MYSQL_ROOT_PASSWORD: root_secret\n");
            sb.append("    ports:\n");
            sb.append("      - \"3306:3306\"\n");
            sb.append("    volumes:\n");
            sb.append("      - mysqldata:/var/lib/mysql\n\n");
        } else {
            // H2
            sb.append("    restart: always\n");
        }

        if (file.hasStorage()) {
            sb.append("  minio:\n");
            sb.append("    image: minio/minio:latest\n");
            sb.append("    command: server /data --console-address \":9001\"\n");
            sb.append("    environment:\n");
            sb.append("      MINIO_ROOT_USER: minioadmin\n");
            sb.append("      MINIO_ROOT_PASSWORD: minioadmin\n");
            sb.append("    ports:\n");
            sb.append("      - \"9000:9000\"\n");
            sb.append("      - \"9001:9001\"\n");
            sb.append("    volumes:\n");
            sb.append("      - miniodata:/data\n\n");
        }

        List<String> volumes = new ArrayList<>();
        if ("postgres".equals(db) || "postgresql".equals(db)) {
            volumes.add("  pgdata:");
        } else if ("mysql".equals(db)) {
            volumes.add("  mysqldata:");
        }
        if (file.hasStorage()) {
            volumes.add("  miniodata:");
        }

        if (!volumes.isEmpty()) {
            sb.append("volumes:\n");
            for (String v : volumes) {
                sb.append(v).append("\n");
            }
        }

        return new JavaSourceFile("docker-compose.yml", sb.toString());
    }

    private static JavaSourceFile generateDockerignore() {
        String content = "target/\n" +
                ".git/\n" +
                ".gitignore\n" +
                "*.md\n" +
                ".idea/\n" +
                "*.iml\n" +
                "sdk/\n";
        return new JavaSourceFile(".dockerignore", content);
    }
}
