package dev.zenith.generator;

import dev.zenith.ast.*;

public class MapperGenerator {

    public static JavaSourceFile generate(ModelDef model, ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String className = model.getName();
        String mapperName = className + "Mapper";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".mapper;\n\n");

        sb.append("import ").append(basePkg).append(".entity.").append(className).append(";\n");
        sb.append("import ").append(basePkg).append(".dto.*;\n");
        sb.append("import org.springframework.stereotype.Component;\n\n");

        sb.append("@Component\n");
        sb.append("public class ").append(mapperName).append(" {\n\n");

        // toEntity(CreateRequest)
        sb.append("    public ").append(className).append(" toEntity(Create").append(className).append("Request request) {\n");
        sb.append("        if (request == null) return null;\n");
        sb.append("        ").append(className).append(" entity = new ").append(className).append("();\n");
        for (FieldDef field : model.getFields()) {
            if (field.hasAnnotation("id") || field.hasAnnotation("auto") || field.hasAnnotation("autoNow") || field.hasAnnotation("autoUpdate")) {
                continue;
            }
            String cap = TypeHelper.capitalize(field.getName());
            sb.append("        entity.set").append(cap).append("(request.").append(field.getName()).append("());\n");
        }
        sb.append("        return entity;\n");
        sb.append("    }\n\n");

        // updateEntity(Entity, UpdateRequest)
        sb.append("    public void updateEntity(").append(className).append(" entity, Update").append(className).append("Request request) {\n");
        sb.append("        if (entity == null || request == null) return;\n");
        for (FieldDef field : model.getFields()) {
            if (field.hasAnnotation("id") || field.hasAnnotation("auto") || field.hasAnnotation("autoNow") || field.hasAnnotation("autoUpdate")) {
                continue;
            }
            String cap = TypeHelper.capitalize(field.getName());
            sb.append("        if (request.").append(field.getName()).append("() != null) {\n");
            sb.append("            entity.set").append(cap).append("(request.").append(field.getName()).append("());\n");
            sb.append("        }\n");
        }
        sb.append("    }\n\n");

        // toResponse(Entity)
        sb.append("    public ").append(className).append("Response toResponse(").append(className).append(" entity) {\n");
        sb.append("        if (entity == null) return null;\n");
        sb.append("        return new ").append(className).append("Response(\n");

        java.util.List<String> args = new java.util.ArrayList<>();
        for (FieldDef field : model.getFields()) {
            if (field.hasAnnotation("password")) {
                continue;
            }
            String cap = TypeHelper.capitalize(field.getName());
            args.add("            entity.get" + cap + "()");
        }
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                String cap = TypeHelper.capitalize(rel.getFieldName());
                args.add("            entity.get" + cap + "() != null ? entity.get" + cap + "().getId() : null");
            }
        }
        sb.append(String.join(",\n", args));
        sb.append("\n        );\n");
        sb.append("    }\n");

        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/mapper/" + mapperName + ".java";
        return new JavaSourceFile(path, sb.toString());
    }
}
