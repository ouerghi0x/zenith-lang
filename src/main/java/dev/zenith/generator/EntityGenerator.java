package dev.zenith.generator;

import dev.zenith.ast.*;

import java.util.ArrayList;
import java.util.List;

public class EntityGenerator {

    public static JavaSourceFile generate(ModelDef model, ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String className = model.getName();
        String tableName = TypeHelper.toSnakeCase(TypeHelper.pluralize(className));

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".entity;\n\n");

        sb.append("import jakarta.persistence.*;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.ArrayList;\n");
        sb.append("import java.util.Objects;\n");

        // Check if enums are imported
        sb.append("import ").append(basePkg).append(".model.*;\n\n");

        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(tableName).append("\")\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Fields
        List<FieldDef> autoNowFields = new ArrayList<>();
        List<FieldDef> autoUpdateFields = new ArrayList<>();

        for (FieldDef field : model.getFields()) {
            String fieldType = TypeHelper.toSimpleJavaType(field.getType());
            String colName = TypeHelper.toSnakeCase(field.getName());

            if (field.hasAnnotation("id")) {
                sb.append("    @Id\n");
                if (field.hasAnnotation("auto")) {
                    sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
                }
            }

            boolean isEnum = file.findEnum(field.getType().getName()) != null;
            if (isEnum) {
                sb.append("    @Enumerated(EnumType.STRING)\n");
            }

            // Column attributes
            List<String> colAttrs = new ArrayList<>();
            colAttrs.add("name = \"" + colName + "\"");
            if (!field.isOptional() && !field.hasAnnotation("id")) {
                colAttrs.add("nullable = false");
            }
            if (field.hasAnnotation("unique")) {
                colAttrs.add("unique = true");
            }
            if (field.hasAnnotation("text")) {
                colAttrs.add("columnDefinition = \"TEXT\"");
            }

            sb.append("    @Column(").append(String.join(", ", colAttrs)).append(")\n");
            sb.append("    private ").append(fieldType).append(" ").append(field.getName()).append(";\n\n");

            if (field.hasAnnotation("autoNow")) {
                autoNowFields.add(field);
            }
            if (field.hasAnnotation("autoUpdate")) {
                autoUpdateFields.add(field);
            }
        }

        // Relations
        for (RelationDef rel : model.getRelations()) {
            String targetModel = rel.getTargetModel();
            String fieldName = rel.getFieldName();

            if (rel.getRelationType() == RelationType.MANY_TO_ONE) {
                sb.append("    @ManyToOne(fetch = FetchType.LAZY)\n");
                sb.append("    @JoinColumn(name = \"").append(TypeHelper.toSnakeCase(fieldName)).append("_id\")\n");
                sb.append("    private ").append(targetModel).append(" ").append(fieldName).append(";\n\n");
            } else if (rel.getRelationType() == RelationType.ONE_TO_MANY) {
                String mappedBy = rel.getMappedBy() != null ? rel.getMappedBy() : TypeHelper.toSnakeCase(className);
                sb.append("    @OneToMany(mappedBy = \"").append(mappedBy).append("\", cascade = CascadeType.ALL, orphanRemoval = true)\n");
                sb.append("    private List<").append(targetModel).append("> ").append(fieldName).append(" = new ArrayList<>();\n\n");
            } else if (rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append("    @OneToOne(fetch = FetchType.LAZY)\n");
                sb.append("    @JoinColumn(name = \"").append(TypeHelper.toSnakeCase(fieldName)).append("_id\")\n");
                sb.append("    private ").append(targetModel).append(" ").append(fieldName).append(";\n\n");
            } else if (rel.getRelationType() == RelationType.MANY_TO_MANY) {
                sb.append("    @ManyToMany\n");
                sb.append("    @JoinTable(\n");
                sb.append("        name = \"").append(tableName).append("_").append(TypeHelper.toSnakeCase(fieldName)).append("\",\n");
                sb.append("        joinColumns = @JoinColumn(name = \"").append(TypeHelper.toSnakeCase(className)).append("_id\"),\n");
                sb.append("        inverseJoinColumns = @JoinColumn(name = \"").append(TypeHelper.toSnakeCase(targetModel)).append("_id\")\n");
                sb.append("    )\n");
                sb.append("    private List<").append(targetModel).append("> ").append(fieldName).append(" = new ArrayList<>();\n\n");
            }
        }

        // PrePersist & PreUpdate Lifecycle hooks
        if (!autoNowFields.isEmpty() || !autoUpdateFields.isEmpty()) {
            sb.append("    @PrePersist\n");
            sb.append("    public void prePersist() {\n");
            for (FieldDef f : autoNowFields) {
                sb.append("        if (this.").append(f.getName()).append(" == null) {\n");
                sb.append("            this.").append(f.getName()).append(" = LocalDateTime.now();\n");
                sb.append("        }\n");
            }
            for (FieldDef f : autoUpdateFields) {
                sb.append("        this.").append(f.getName()).append(" = LocalDateTime.now();\n");
            }
            sb.append("    }\n\n");

            if (!autoUpdateFields.isEmpty()) {
                sb.append("    @PreUpdate\n");
                sb.append("    public void preUpdate() {\n");
                for (FieldDef f : autoUpdateFields) {
                    sb.append("        this.").append(f.getName()).append(" = LocalDateTime.now();\n");
                }
                sb.append("    }\n\n");
            }
        }

        // Constructors
        sb.append("    public ").append(className).append("() {}\n\n");

        // Getters and Setters
        for (FieldDef field : model.getFields()) {
            String fieldType = TypeHelper.toSimpleJavaType(field.getType());
            String cap = TypeHelper.capitalize(field.getName());

            sb.append("    public ").append(fieldType).append(" get").append(cap).append("() {\n");
            sb.append("        return this.").append(field.getName()).append(";\n");
            sb.append("    }\n\n");

            sb.append("    public void set").append(cap).append("(").append(fieldType).append(" ").append(field.getName()).append(") {\n");
            sb.append("        this.").append(field.getName()).append(" = ").append(field.getName()).append(";\n");
            sb.append("    }\n\n");
        }

        for (RelationDef rel : model.getRelations()) {
            String fieldName = rel.getFieldName();
            String cap = TypeHelper.capitalize(fieldName);
            String fieldType = rel.isCollection() ? "List<" + rel.getTargetModel() + ">" : rel.getTargetModel();

            sb.append("    public ").append(fieldType).append(" get").append(cap).append("() {\n");
            sb.append("        return this.").append(fieldName).append(";\n");
            sb.append("    }\n\n");

            sb.append("    public void set").append(cap).append("(").append(fieldType).append(" ").append(fieldName).append(") {\n");
            sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
            sb.append("    }\n\n");
        }

        // equals and hashCode
        FieldDef idField = model.getIdField();
        if (idField != null) {
            sb.append("    @Override\n");
            sb.append("    public boolean equals(Object o) {\n");
            sb.append("        if (this == o) return true;\n");
            sb.append("        if (o == null || getClass() != o.getClass()) return false;\n");
            sb.append("        ").append(className).append(" that = (").append(className).append(") o;\n");
            sb.append("        return Objects.equals(").append(idField.getName()).append(", that.").append(idField.getName()).append(");\n");
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public int hashCode() {\n");
            sb.append("        return Objects.hash(").append(idField.getName()).append(");\n");
            sb.append("    }\n");
        }

        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/entity/" + className + ".java";
        return new JavaSourceFile(path, sb.toString());
    }
}
