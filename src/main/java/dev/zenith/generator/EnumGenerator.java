package dev.zenith.generator;

import dev.zenith.ast.EnumDef;
import dev.zenith.ast.ZenithFile;

public class EnumGenerator {

    public static JavaSourceFile generate(EnumDef enumDef, ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String enumName = enumDef.getName();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".model;\n\n");
        sb.append("public enum ").append(enumName).append(" {\n");
        sb.append("    ").append(String.join(", ", enumDef.getValues())).append("\n");
        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/model/" + enumName + ".java";
        return new JavaSourceFile(path, sb.toString());
    }
}
