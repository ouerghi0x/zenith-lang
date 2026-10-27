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
        sb.append("import ").append(basePkg).append(".model.*;\n");
        sb.append("import org.springframework.data.jpa.repository.JpaRepository;\n");
        sb.append("import org.springframework.data.jpa.repository.Query;\n");
        sb.append("import org.springframework.data.repository.query.Param;\n");
        sb.append("import org.springframework.data.domain.Page;\n");
        sb.append("import org.springframework.data.domain.Pageable;\n");
        sb.append("import org.springframework.stereotype.Repository;\n");
        sb.append("import java.util.Optional;\n");
        sb.append("import java.util.List;\n\n");

        sb.append("@Repository\n");
        sb.append("public interface ").append(repoName).append(" extends JpaRepository<").append(className).append(", ").append(idType).append("> {\n\n");

        // Unique field queries
        for (FieldDef field : model.getFields()) {
            if (field.hasAnnotation("unique") && !field.hasAnnotation("id")) {
                String cap = TypeHelper.capitalize(field.getName());
                String type = TypeHelper.toSimpleJavaType(field.getType());
                sb.append("    Optional<").append(className).append("> findBy").append(cap).append("(").append(type).append(" ").append(field.getName()).append(");\n");
                sb.append("    boolean existsBy").append(cap).append("(").append(type).append(" ").append(field.getName()).append(");\n\n");
            }
        }

        // Search fields query
        CrudConfig crud = model.getCrudConfig();
        if (crud != null && !crud.getSearchFields().isEmpty()) {
            List<String> searchParts = new ArrayList<>();
            for (String sf : crud.getSearchFields()) {
                searchParts.add("LOWER(e." + sf + ") LIKE LOWER(CONCAT('%', :keyword, '%'))");
            }
            sb.append("    @Query(\"SELECT e FROM ").append(className).append(" e WHERE ").append(String.join(" OR ", searchParts)).append("\")\n");
            sb.append("    Page<").append(className).append("> search(@Param(\"keyword\") String keyword, Pageable pageable);\n\n");
        }

        // Relation filter queries
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE) {
                String cap = TypeHelper.capitalize(rel.getFieldName());
                sb.append("    Page<").append(className).append("> findBy").append(cap).append("Id(Long ").append(rel.getFieldName()).append("Id, Pageable pageable);\n\n");
            }
        }

        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/repository/" + repoName + ".java";
        return new JavaSourceFile(path, sb.toString());
    }
}
