package dev.zenith.generator;

import dev.zenith.ast.*;

import java.util.ArrayList;
import java.util.List;

public class RepositoryGenerator {

    public static JavaSourceFile generate(ModelDef model, ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String className = model.getName();
        String repoName = className + "Repository";

        FieldDef idField = model.getIdField();
        String idType = idField != null ? TypeHelper.toSimpleJavaType(idField.getType()) : "Long";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".repository;\n\n");

        sb.append("import ").append(basePkg).append(".entity.").append(className).append(";\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("import org.springframework.data.jpa.repository.JpaRepository;\n");
        sb.append("import org.springframework.data.jpa.repository.JpaSpecificationExecutor;\n");
        sb.append("import org.springframework.data.jpa.repository.Query;\n");
        sb.append("import org.springframework.data.repository.query.Param;\n");
        sb.append("import org.springframework.data.domain.Page;\n");
        sb.append("import org.springframework.data.domain.Pageable;\n");
        sb.append("import org.springframework.stereotype.Repository;\n");
        sb.append("import java.util.Optional;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("@Repository\n");
        sb.append("public interface ").append(repoName).append(" extends JpaRepository<").append(className).append(", ")
                .append(idType).append(">, JpaSpecificationExecutor<").append(className).append("> {\n\n");

        // Unique field queries
        for (FieldDef field : model.getFields()) {
            if (field.hasAnnotation("unique") && !field.hasAnnotation("id")) {
                String cap = TypeHelper.capitalize(field.getName());
                String type = TypeHelper.toSimpleJavaType(field.getType());
                sb.append("    Optional<").append(className).append("> findBy").append(cap).append("(").append(type)
                        .append(" ").append(field.getName()).append(");\n");
                sb.append("    boolean existsBy").append(cap).append("(").append(type).append(" ")
                        .append(field.getName()).append(");\n\n");
            }
        }

        // Search fields query
        CrudConfig crud = model.getCrudConfig();
        if (crud != null && !crud.getSearchFields().isEmpty()) {
            List<String> searchParts = new ArrayList<>();
            for (String sf : crud.getSearchFields()) {
                searchParts.add("LOWER(e." + sf + ") LIKE LOWER(CONCAT('%', :keyword, '%'))");
            }
            sb.append("    @Query(\"SELECT e FROM ").append(className).append(" e WHERE ")
                    .append(String.join(" OR ", searchParts)).append("\")\n");
            sb.append("    Page<").append(className)
                    .append("> search(@Param(\"keyword\") String keyword, Pageable pageable);\n\n");
        }

        // Relation filter queries
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE) {
                String cap = TypeHelper.capitalize(rel.getFieldName());
                String fkType = DtoGenerator.resolveTargetIdType(rel.getTargetModel(), file);
                sb.append("    Page<").append(className).append("> findBy").append(cap).append("Id(").append(fkType)
                        .append(" ")
                        .append(rel.getFieldName()).append("Id, Pageable pageable);\n\n");
                sb.append("    List<").append(className).append("> findBy").append(cap).append("Id(").append(fkType)
                        .append(" ")
                        .append(rel.getFieldName()).append("Id);\n\n");

                ModelDef userModel = file.getUserModel();
                if (userModel != null && userModel.getName().equals(rel.getTargetModel())) {
                    FieldDef idFieldDef = userModel.findField("email");
                    if (idFieldDef == null) idFieldDef = userModel.findField("username");
                    if (idFieldDef == null) idFieldDef = userModel.findField("name");
                    String idName = idFieldDef != null ? TypeHelper.capitalize(idFieldDef.getName()) : "Email";
                    String idTypeStr = idFieldDef != null ? TypeHelper.toSimpleJavaType(idFieldDef.getType()) : "String";
                    sb.append("    List<").append(className).append("> findBy").append(cap).append(idName)
                            .append("(").append(idTypeStr).append(" ").append(rel.getFieldName()).append(idName).append(");\n\n");
                }
            }
        }

        // Custom endpoint query methods
        for (dev.zenith.ast.CustomEndpointDef custom : model.getCustomEndpoints()) {
            List<TypeHelper.PathVariable> vars = TypeHelper.extractPathVariables(custom.getPath(), model, idType, custom);
            for (TypeHelper.PathVariable v : vars) {
                if ("id".equalsIgnoreCase(v.getName())) continue;
                FieldDef f = null;
                if (custom.getQuery() != null && !custom.getQuery().isEmpty()) {
                    String q = custom.getQuery().replaceAll("\\s+", "");
                    String fn = q.contains("==") ? q.split("==")[0] : q;
                    f = model.findField(fn);
                }
                if (f == null) f = model.findField(v.getName());
                if (f != null && !f.hasAnnotation("unique") && !f.hasAnnotation("id")) {
                    String cap = TypeHelper.capitalize(f.getName());
                    String type = TypeHelper.toSimpleJavaType(f.getType());
                    sb.append("    Optional<").append(className).append("> findBy").append(cap).append("(").append(type)
                            .append(" ").append(f.getName()).append(");\n\n");
                }
            }
        }

        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/repository/" + repoName + ".java";
        return new JavaSourceFile(path, sb.toString());
    }
}
