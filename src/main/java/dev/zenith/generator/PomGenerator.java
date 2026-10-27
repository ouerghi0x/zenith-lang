package dev.zenith.generator;

import dev.zenith.ast.ZenithFile;

public class PomGenerator {

    public static JavaSourceFile generate(ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String appName = file.getConfig().getApp();
        String artifactId = TypeHelper.toSnakeCase(appName).replace('_', '-');
        String sbVersion = file.getConfig().getSpringBootVersion();
        int javaVer = file.getConfig().getJavaVersion();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("    <modelVersion>4.0.0</modelVersion>\n\n");
        sb.append("    <parent>\n");
        sb.append("        <groupId>org.springframework.boot</groupId>\n");
        sb.append("        <artifactId>spring-boot-starter-parent</artifactId>\n");
        sb.append("        <version>").append(sbVersion).append("</version>\n");
        sb.append("        <relativePath/>\n");
        sb.append("    </parent>\n\n");
        sb.append("    <groupId>").append(basePkg).append("</groupId>\n");
        sb.append("    <artifactId>").append(artifactId).append("</artifactId>\n");
        sb.append("    <version>1.0.0</version>\n");
        sb.append("    <name>").append(appName).append("</name>\n");
        sb.append("    <description>Generated with Zenith DSL</description>\n\n");
        sb.append("    <properties>\n");
        sb.append("        <java.version>").append(javaVer).append("</java.version>\n");
        sb.append("    </properties>\n\n");
        sb.append("    <dependencies>\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.springframework.boot</groupId>\n");
        sb.append("            <artifactId>spring-boot-starter-web</artifactId>\n");
        sb.append("        </dependency>\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.springframework.boot</groupId>\n");
        sb.append("            <artifactId>spring-boot-starter-data-jpa</artifactId>\n");
        sb.append("        </dependency>\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.springframework.boot</groupId>\n");
        sb.append("            <artifactId>spring-boot-starter-validation</artifactId>\n");
        sb.append("        </dependency>\n");

        if (file.getConfig().isSwaggerEnabled()) {
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.springdoc</groupId>\n");
            sb.append("            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>\n");
            sb.append("            <version>2.3.0</version>\n");
            sb.append("        </dependency>\n");
        }

        if (file.getConfig().isFlywayEnabled()) {
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.flywaydb</groupId>\n");
            sb.append("            <artifactId>flyway-core</artifactId>\n");
            sb.append("        </dependency>\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.flywaydb</groupId>\n");
            sb.append("            <artifactId>flyway-database-postgresql</artifactId>\n");
            sb.append("        </dependency>\n");
        }

        if (file.hasAuth()) {
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.springframework.boot</groupId>\n");
            sb.append("            <artifactId>spring-boot-starter-security</artifactId>\n");
            sb.append("        </dependency>\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>io.jsonwebtoken</groupId>\n");
            sb.append("            <artifactId>jjwt-api</artifactId>\n");
            sb.append("            <version>0.12.5</version>\n");
            sb.append("        </dependency>\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>io.jsonwebtoken</groupId>\n");
            sb.append("            <artifactId>jjwt-impl</artifactId>\n");
            sb.append("            <version>0.12.5</version>\n");
            sb.append("            <scope>runtime</scope>\n");
            sb.append("        </dependency>\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>io.jsonwebtoken</groupId>\n");
            sb.append("            <artifactId>jjwt-jackson</artifactId>\n");
            sb.append("            <version>0.12.5</version>\n");
            sb.append("            <scope>runtime</scope>\n");
            sb.append("        </dependency>\n");
        }

        sb.append("        <dependency>\n");
        sb.append("            <groupId>com.h2database</groupId>\n");
        sb.append("            <artifactId>h2</artifactId>\n");
        sb.append("            <scope>runtime</scope>\n");
        sb.append("        </dependency>\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.postgresql</groupId>\n");
        sb.append("            <artifactId>postgresql</artifactId>\n");
        sb.append("            <scope>runtime</scope>\n");
        sb.append("        </dependency>\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.springframework.boot</groupId>\n");
        sb.append("            <artifactId>spring-boot-starter-test</artifactId>\n");
        sb.append("            <scope>test</scope>\n");
        sb.append("        </dependency>\n");
        sb.append("    </dependencies>\n\n");
        sb.append("    <build>\n");
        sb.append("        <plugins>\n");
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.springframework.boot</groupId>\n");
        sb.append("                <artifactId>spring-boot-maven-plugin</artifactId>\n");
        sb.append("            </plugin>\n");
        sb.append("        </plugins>\n");
        sb.append("    </build>\n");
        sb.append("</project>\n");

        return new JavaSourceFile("pom.xml", sb.toString());
    }
}
