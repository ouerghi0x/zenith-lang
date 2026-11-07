package dev.zenith.generator;

import dev.zenith.ast.*;

import java.util.ArrayList;
import java.util.List;

public class DtoGenerator {

    public static List<JavaSourceFile> generate(ModelDef model, ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        String basePkg = file.getConfig().getPackage();
        String pkgDir = "src/main/java/" + basePkg.replace('.', '/') + "/dto/";

        boolean isAuthUser = file.hasAuth() && file.getUserModel() != null
                && file.getUserModel().getName().equals(model.getName());
        if (model.getCrudConfig() != null || isAuthUser) {
            list.add(generateCreateRequest(model, file, pkgDir, basePkg));
            list.add(generateUpdateRequest(model, file, pkgDir, basePkg));
        }
        list.add(generateResponse(model, file, pkgDir, basePkg));

        return list;
    }

    public static List<JavaSourceFile> generateJustDto(DtoDef dto, ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        String basePkg = file.getConfig().getPackage();
        String pkgDir = "src/main/java/" + basePkg.replace('.', '/') + "/dto/";

        list.add(generateDtoBody(dto, file, pkgDir, basePkg));

        return list;
    }

    private static JavaSourceFile generateDtoBody(DtoDef dto, ZenithFile file, String pkgDir, String basePkg) {
        var className = dto.getName();
        className = Character.toUpperCase(className.charAt(0)) + className.substring(1);
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".dto;\n\n");

        sb.append("import jakarta.validation.constraints.*;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.util.List;\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("\n");

        sb.append("public record ").append(className).append("(\n");

        List<String> recordComponents = new ArrayList<>();
        for (FieldDef field : dto.getFields()) {
            if (field.hasAnnotation("id") || field.hasAnnotation("auto") || field.hasAnnotation("autoNow")
                    || field.hasAnnotation("autoUpdate")) {
                continue;
            }
            StringBuilder comp = new StringBuilder();
            List<String> validations = TypeHelper.toValidationAnnotations(field);
            for (String v : validations) {
                comp.append("    ").append(v).append("\n");
            }
            comp.append("    ").append(TypeHelper.toSimpleJavaType(field.getType())).append(" ")
                    .append(field.getName());
            recordComponents.add(comp.toString());
        }

        sb.append(String.join(",\n", recordComponents));
        sb.append("\n) {}\n");

        return new JavaSourceFile(pkgDir + className + ".java", sb.toString());
    }

    public static JavaSourceFile generatePageResponse(ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".dto;\n\n");
        sb.append("import java.util.List;\n\n");
        sb.append("public record PageResponse<T>(\n");
        sb.append("    List<T> content,\n");
        sb.append("    int page,\n");
        sb.append("    int size,\n");
        sb.append("    long totalElements,\n");
        sb.append("    int totalPages,\n");
        sb.append("    boolean isFirst,\n");
        sb.append("    boolean isLast\n");
        sb.append(") {\n");
        sb.append("    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> springPage) {\n");
        sb.append("        return new PageResponse<>(\n");
        sb.append("            springPage.getContent(),\n");
        sb.append("            springPage.getNumber(),\n");
        sb.append("            springPage.getSize(),\n");
        sb.append("            springPage.getTotalElements(),\n");
        sb.append("            springPage.getTotalPages(),\n");
        sb.append("            springPage.isFirst(),\n");
        sb.append("            springPage.isLast()\n");
        sb.append("        );\n");
        sb.append("    }\n");
        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/dto/PageResponse.java";
        return new JavaSourceFile(path, sb.toString());
    }

    private static JavaSourceFile generateCreateRequest(ModelDef model, ZenithFile file, String pkgDir,
            String basePkg) {
        String className = "Create" + model.getName() + "Request";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".dto;\n\n");

        sb.append("import jakarta.validation.constraints.*;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.util.List;\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("\n");

        sb.append("public record ").append(className).append("(\n");

        List<String> recordComponents = new ArrayList<>();
        for (FieldDef field : model.getFields()) {
            if (field.hasAnnotation("id") || field.hasAnnotation("auto") || field.hasAnnotation("autoNow")
                    || field.hasAnnotation("autoUpdate")) {
                continue;
            }
            StringBuilder comp = new StringBuilder();
            List<String> validations = TypeHelper.toValidationAnnotations(field);
            for (String v : validations) {
                comp.append("    ").append(v).append("\n");
            }
            comp.append("    ").append(TypeHelper.toSimpleJavaType(field.getType())).append(" ")
                    .append(field.getName());
            recordComponents.add(comp.toString());
        }

        // Add relation foreign keys (e.g. Long/UUID categoryId)
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                String fkType = resolveTargetIdType(rel.getTargetModel(), file);
                StringBuilder comp = new StringBuilder();
                if (!rel.isOptional()) {
                    comp.append("    @NotNull(message = \"").append(rel.getFieldName())
                            .append("Id cannot be null\")\n");
                }
                comp.append("    ").append(fkType).append(" ").append(rel.getFieldName()).append("Id");
                recordComponents.add(comp.toString());
            }
        }

        sb.append(String.join(",\n", recordComponents));
        sb.append("\n) {}\n");

        return new JavaSourceFile(pkgDir + className + ".java", sb.toString());
    }

    private static JavaSourceFile generateUpdateRequest(ModelDef model, ZenithFile file, String pkgDir,
            String basePkg) {
        String className = "Update" + model.getName() + "Request";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".dto;\n\n");

        sb.append("import jakarta.validation.constraints.*;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.util.List;\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("\n");

        sb.append("public record ").append(className).append("(\n");

        List<String> recordComponents = new ArrayList<>();
        for (FieldDef field : model.getFields()) {
            if (field.hasAnnotation("id") || field.hasAnnotation("auto") || field.hasAnnotation("autoNow")
                    || field.hasAnnotation("autoUpdate")) {
                continue;
            }
            StringBuilder comp = new StringBuilder();
            List<String> validations = TypeHelper.toValidationAnnotationsWithoutNullCheck(field);
            for (String v : validations) {
                comp.append("    ").append(v).append("\n");
            }
            comp.append("    ").append(TypeHelper.toSimpleJavaType(field.getType())).append(" ")
                    .append(field.getName());
            recordComponents.add(comp.toString());
        }

        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                String fkType = resolveTargetIdType(rel.getTargetModel(), file);
                recordComponents.add("    " + fkType + " " + rel.getFieldName() + "Id");
            }
        }

        sb.append(String.join(",\n", recordComponents));
        sb.append("\n) {}\n");

        return new JavaSourceFile(pkgDir + className + ".java", sb.toString());
    }

    private static JavaSourceFile generateResponse(ModelDef model, ZenithFile file, String pkgDir, String basePkg) {
        String className = model.getName() + "Response";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".dto;\n\n");

        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.util.List;\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("\n");

        sb.append("public record ").append(className).append("(\n");

        List<String> recordComponents = new ArrayList<>();
        for (FieldDef field : model.getFields()) {
            if (field.hasAnnotation("password")) {
                continue; // Never expose passwords in response DTOs
            }
            recordComponents.add("    " + TypeHelper.toSimpleJavaType(field.getType()) + " " + field.getName());
        }

        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                String fkType = resolveTargetIdType(rel.getTargetModel(), file);
                recordComponents.add("    " + fkType + " " + rel.getFieldName() + "Id");
            }
        }

        sb.append(String.join(",\n", recordComponents));
        sb.append("\n) {}\n");

        return new JavaSourceFile(pkgDir + className + ".java", sb.toString());
    }

    public static String resolveTargetIdType(String targetModelName, ZenithFile file) {
        for (ModelDef m : file.getModels()) {
            if (m.getName().equalsIgnoreCase(targetModelName)) {
                FieldDef idField = m.getIdField();
                if (idField != null) {
                    return TypeHelper.toSimpleJavaType(idField.getType());
                }
            }
        }
        return "Long";
    }
}
