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
        sb.append("import java.util.UUID;\n");
        sb.append("import org.springframework.stereotype.Component;\n\n");

        sb.append("@Component\n");
        sb.append("public class ").append(mapperName).append(" {\n\n");

        boolean isAuthUser = file.hasAuth() && file.getUserModel() != null && file.getUserModel().getName().equals(model.getName());
        if (model.getCrudConfig() != null || isAuthUser) {
            // toEntity(CreateRequest)
            sb.append("    public ").append(className).append(" toEntity(Create").append(className)
                    .append("Request request) {\n");
            sb.append("        if (request == null) return null;\n");
            sb.append("        ").append(className).append(" entity = new ").append(className).append("();\n");
            for (FieldDef field : model.getFields()) {
                if (field.hasAnnotation("id") || field.hasAnnotation("auto") || field.hasAnnotation("autoNow")
                        || field.hasAnnotation("autoUpdate")) {
                    continue;
                }
                String cap = TypeHelper.capitalize(field.getName());
                sb.append("        entity.set").append(cap).append("(request.").append(field.getName()).append("());\n");
            }
            sb.append("        return entity;\n");
            sb.append("    }\n\n");

            // updateEntity(Entity, UpdateRequest)
            sb.append("    public void updateEntity(").append(className).append(" entity, Update").append(className)
                    .append("Request request) {\n");
            sb.append("        if (entity == null || request == null) return;\n");
            for (FieldDef field : model.getFields()) {
                if (field.hasAnnotation("id") || field.hasAnnotation("auto") || field.hasAnnotation("autoNow")
                        || field.hasAnnotation("autoUpdate")) {
                    continue;
                }
                String cap = TypeHelper.capitalize(field.getName());
                sb.append("        if (request.").append(field.getName()).append("() != null) {\n");
                sb.append("            entity.set").append(cap).append("(request.").append(field.getName())
                        .append("());\n");
                sb.append("        }\n");
            }
            sb.append("    }\n\n");
        }

        // toResponse(Entity)
        sb.append("    public ").append(className).append("Response toResponse(").append(className)
                .append(" entity) {\n");
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

        // Custom DTO mappings for endpoints
        java.util.Set<String> processedCustomDtos = new java.util.HashSet<>();
        for (CustomEndpointDef custom : model.getCustomEndpoints()) {
            String ret = custom.getReturnType();
            if (ret == null || ret.equalsIgnoreCase("void") || ret.equalsIgnoreCase(className + "Response")
                    || ret.equalsIgnoreCase(className)) {
                continue;
            }
            if (ret.startsWith("List<") && ret.endsWith(">")) {
                ret = ret.substring(5, ret.length() - 1).trim();
            }
            if (processedCustomDtos.contains(ret)) {
                continue;
            }

            DtoDef dto = file.findDto(ret);
            if (dto != null) {
                processedCustomDtos.add(ret);
                sb.append("\n    public ").append(ret).append(" to").append(ret).append("(")
                        .append(className).append(" entity) {\n");
                sb.append("        if (entity == null) return null;\n");
                sb.append("        return new ").append(ret).append("(\n");

                java.util.List<String> dArgs = new java.util.ArrayList<>();
                for (FieldDef dField : dto.getFields()) {
                    String dName = dField.getName();
                    FieldDef mField = model.findField(dName);
                    if (mField != null) {
                        String cap = TypeHelper.capitalize(mField.getName());
                        dArgs.add("            entity.get" + cap + "()");
                    } else {
                        // Check if relation matches, e.g. customer -> customerId
                        boolean matchedRel = false;
                        for (RelationDef rel : model.getRelations()) {
                            if ((rel.getFieldName() + "Id").equalsIgnoreCase(dName)
                                    || rel.getFieldName().equalsIgnoreCase(dName)) {
                                String cap = TypeHelper.capitalize(rel.getFieldName());
                                dArgs.add("            entity.get" + cap + "() != null ? entity.get" + cap + "().getId() : null");
                                matchedRel = true;
                                break;
                            }
                        }
                        if (!matchedRel) {
                            String defVal = getDefaultLiteral(dField.getType());
                            dArgs.add("            " + defVal);
                        }
                    }
                }
                sb.append(String.join(",\n", dArgs));
                sb.append("\n        );\n");
                sb.append("    }\n");
            }
        }

        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/mapper/" + mapperName + ".java";
        return new JavaSourceFile(path, sb.toString());
    }

    private static String getDefaultLiteral(TypeRef type) {
        return TypeHelper.getDefaultLiteral(type);
    }
}
