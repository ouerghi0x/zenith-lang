package dev.zenith.generator;

import dev.zenith.ast.ZenithFile;

public class ApplicationGenerator {

    public static JavaSourceFile generate(ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String appName = file.getConfig().getApp();
        if (!appName.endsWith("Application")) {
            appName = appName + "Application";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(";\n\n");
        sb.append("import org.springframework.boot.SpringApplication;\n");
        sb.append("import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n");
        sb.append("@SpringBootApplication\n");
        sb.append("public class ").append(appName).append(" {\n\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        SpringApplication.run(").append(appName).append(".class, args);\n");
        sb.append("    }\n");
        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/" + appName + ".java";
        return new JavaSourceFile(path, sb.toString());
    }
}
