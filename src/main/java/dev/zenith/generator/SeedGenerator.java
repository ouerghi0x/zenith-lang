package dev.zenith.generator;

import dev.zenith.ast.FieldDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.RelationDef;
import dev.zenith.ast.RelationType;
import dev.zenith.ast.SeedDef;
import dev.zenith.ast.SeedRef;
import dev.zenith.ast.ZenithFile;

import java.util.*;

public class SeedGenerator {

    public static JavaSourceFile generate(ZenithFile file) {
        if (file.getSeeds().isEmpty()) {
            return null;
        }

        String basePkg = file.getConfig().getPackage();
        String pkgDir = "src/main/java/" + basePkg.replace('.', '/') + "/config/";

        Set<String> requiredRepoModels = new LinkedHashSet<>();
        for (SeedDef s : file.getSeeds()) {
            requiredRepoModels.add(s.getEntityName());
            ModelDef m = file.findModel(s.getEntityName());
            if (m != null) {
                for (RelationDef r : m.getRelations()) {
                    if (r.getRelationType() == RelationType.MANY_TO_ONE || r.getRelationType() == RelationType.ONE_TO_ONE) {
                        requiredRepoModels.add(r.getTargetModel());
                    }
                }
            }
        }

        boolean hasAuth = file.hasAuth();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".config;\n\n");

        sb.append("import org.springframework.boot.ApplicationArguments;\n");
        sb.append("import org.springframework.boot.ApplicationRunner;\n");
        sb.append("import org.springframework.context.annotation.Profile;\n");
        sb.append("import org.springframework.stereotype.Component;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n");

        if (hasAuth) {
            sb.append("import org.springframework.security.crypto.password.PasswordEncoder;\n");
        }

        sb.append("import ").append(basePkg).append(".entity.*;\n");
        sb.append("import ").append(basePkg).append(".model.*;\n");
        sb.append("import ").append(basePkg).append(".repository.*;\n\n");

        sb.append("@Component\n");
        sb.append("@Profile(\"!prod\")\n");
        sb.append("public class DatabaseSeeder implements ApplicationRunner {\n\n");

        // Repositories fields
        for (String mName : requiredRepoModels) {
            String repoType = mName + "Repository";
            String repoVar = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "Repository";
            sb.append("    private final ").append(repoType).append(" ").append(repoVar).append(";\n");
        }
        if (hasAuth) {
            sb.append("    private final PasswordEncoder passwordEncoder;\n");
        }
        sb.append("\n");

        // Constructor Injection
        sb.append("    public DatabaseSeeder(\n");
        List<String> ctorParams = new ArrayList<>();
        for (String mName : requiredRepoModels) {
            String repoType = mName + "Repository";
            String repoVar = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "Repository";
            ctorParams.add("        " + repoType + " " + repoVar);
        }
        if (hasAuth) {
            ctorParams.add("        PasswordEncoder passwordEncoder");
        }
        sb.append(String.join(",\n", ctorParams));
        sb.append("\n    ) {\n");
        for (String mName : requiredRepoModels) {
            String repoVar = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "Repository";
            sb.append("        this.").append(repoVar).append(" = ").append(repoVar).append(";\n");
        }
        if (hasAuth) {
            sb.append("        this.passwordEncoder = passwordEncoder;\n");
        }
        sb.append("    }\n\n");

        // Dependency sorted seeds
        List<SeedDef> sortedSeeds = new ArrayList<>(file.getSeeds());
        sortedSeeds.sort((s1, s2) -> {
            ModelDef m1 = file.findModel(s1.getEntityName());
            ModelDef m2 = file.findModel(s2.getEntityName());
            if (m1 != null && m2 != null) {
                boolean m1DependsOnM2 = m1.getRelations().stream().anyMatch(r -> r.getTargetModel().equals(m2.getName()));
                boolean m2DependsOnM1 = m2.getRelations().stream().anyMatch(r -> r.getTargetModel().equals(m1.getName()));
                if (m1DependsOnM2 && !m2DependsOnM1) return 1;
                if (m2DependsOnM1 && !m1DependsOnM2) return -1;
            }
            return 0;
        });

        // run method
        sb.append("    @Override\n");
        sb.append("    @Transactional\n");
        sb.append("    public void run(ApplicationArguments args) {\n");
        for (SeedDef seed : sortedSeeds) {
            sb.append("        seed").append(seed.getEntityName()).append("();\n");
        }
        sb.append("    }\n\n");

        // Seeder helper methods
        for (SeedDef seed : file.getSeeds()) {
            String mName = seed.getEntityName();
            ModelDef model = file.findModel(mName);
            String repoVar = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "Repository";

            sb.append("    private void seed").append(mName).append("() {\n");
            sb.append("        if (").append(repoVar).append(".count() > 0) {\n");
            sb.append("            return;\n");
            sb.append("        }\n\n");

            // Pre-fetch related entities for ManyToOne / OneToOne relations
            Set<String> relationTargetModels = new LinkedHashSet<>();
            if (model != null) {
                for (RelationDef rel : model.getRelations()) {
                    if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                        relationTargetModels.add(rel.getTargetModel());
                    }
                }
            }
            for (String targetModelName : relationTargetModels) {
                String targetRepo = Character.toLowerCase(targetModelName.charAt(0)) + targetModelName.substring(1) + "Repository";
                String targetListVar = "all" + TypeHelper.pluralize(targetModelName);
                sb.append("        List<").append(targetModelName).append("> ").append(targetListVar)
                        .append(" = ").append(targetRepo).append(".findAll();\n");
            }
            if (!relationTargetModels.isEmpty()) {
                sb.append("\n");
            }

            int idx = 1;
            for (Map<String, Object> row : seed.getRows()) {
                String varName = "item" + idx;
                sb.append("        ").append(mName).append(" ").append(varName).append(" = new ").append(mName).append("();\n");

                Set<String> setFields = new HashSet<>();

                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String fieldName = entry.getKey();
                    Object val = entry.getValue();

                    FieldDef field = model != null ? model.findField(fieldName) : null;
                    if (field != null) {
                        setFields.add(field.getName().toLowerCase());
                        String cap = TypeHelper.capitalize(field.getName());
                        String javaType = TypeHelper.toSimpleJavaType(field.getType());

                        if (field.hasAnnotation("password") && hasAuth) {
                            String pass = val != null ? val.toString() : "Password123!";
                            sb.append("        ").append(varName).append(".set").append(cap)
                                    .append("(passwordEncoder.encode(\"").append(escapeJava(pass)).append("\"));\n");
                        } else {
                            sb.append("        ").append(varName).append(".set").append(cap)
                                    .append("(").append(formatValue(val, javaType)).append(");\n");
                        }
                    } else if (model != null) {
                        RelationDef rel = model.findRelation(fieldName);
                        if (rel != null && (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE)) {
                            setFields.add(rel.getFieldName().toLowerCase());
                            String cap = TypeHelper.capitalize(rel.getFieldName());
                            String targetModelName = rel.getTargetModel();
                            String targetListVar = "all" + TypeHelper.pluralize(targetModelName);

                            if (val instanceof SeedRef) {
                                SeedRef ref = (SeedRef) val;
                                int rowIdx = ref.getIndex();
                                sb.append("        if (").append(targetListVar).append(".size() > ").append(rowIdx).append(") {\n");
                                sb.append("            ").append(varName).append(".set").append(cap).append("(")
                                        .append(targetListVar).append(".get(").append(rowIdx).append("));\n");
                                sb.append("        }\n");
                            } else if (val instanceof Number) {
                                int rowIdx = ((Number) val).intValue();
                                sb.append("        if (").append(targetListVar).append(".size() > ").append(rowIdx).append(") {\n");
                                sb.append("            ").append(varName).append(".set").append(cap).append("(")
                                        .append(targetListVar).append(".get(").append(rowIdx).append("));\n");
                                sb.append("        }\n");
                            } else if (val != null) {
                                String valStr = escapeJava(val.toString());
                                ModelDef targetModel = file.findModel(targetModelName);
                                sb.append("        ").append(targetListVar).append(".stream()\n");
                                sb.append("            .filter(e -> ");
                                List<String> checks = new ArrayList<>();
                                checks.add("String.valueOf(e.getId()).equals(\"" + valStr + "\")");
                                if (targetModel != null) {
                                    for (FieldDef tf : targetModel.getFields()) {
                                        if ("String".equalsIgnoreCase(tf.getType().getName()) || tf.hasAnnotation("unique")) {
                                            String tCap = TypeHelper.capitalize(tf.getName());
                                            checks.add("(\"" + valStr + "\".equalsIgnoreCase(e.get" + tCap + "()))");
                                        }
                                    }
                                }
                                sb.append(String.join(" || ", checks));
                                sb.append(")\n            .findFirst().ifPresent(").append(varName).append("::set").append(cap).append(");\n");
                            }
                        }
                    }
                }

                // Default required relations if not set
                if (model != null) {
                    for (RelationDef rel : model.getRelations()) {
                        if (!rel.isOptional() && (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE)) {
                            String cap = TypeHelper.capitalize(rel.getFieldName());
                            String targetModelName = rel.getTargetModel();
                            String targetListVar = "all" + TypeHelper.pluralize(targetModelName);
                            sb.append("        if (").append(varName).append(".get").append(cap).append("() == null && !")
                                    .append(targetListVar).append(".isEmpty()) {\n");
                            sb.append("            ").append(varName).append(".set").append(cap).append("(")
                                    .append(targetListVar).append(".get(0));\n");
                            sb.append("        }\n");
                        }
                    }
                }

                // If user model has a password field that wasn't in the seed row, provide a default encoded password
                if (model != null && hasAuth) {
                    for (FieldDef f : model.getFields()) {
                        if (f.hasAnnotation("password") && !setFields.contains(f.getName().toLowerCase())) {
                            String cap = TypeHelper.capitalize(f.getName());
                            sb.append("        ").append(varName).append(".set").append(cap)
                                    .append("(passwordEncoder.encode(\"Password123!\"));\n");
                        }
                    }
                }

                sb.append("        ").append(repoVar).append(".save(").append(varName).append(");\n\n");
                idx++;
            }
            sb.append("    }\n\n");
        }

        sb.append("}\n");

        return new JavaSourceFile(pkgDir + "DatabaseSeeder.java", sb.toString());
    }

    private static String formatValue(Object val, String javaType) {
        if (val == null) return "null";
        String s = val.toString();

        if ("String".equals(javaType)) {
            return "\"" + escapeJava(s) + "\"";
        } else if ("Long".equals(javaType)) {
            return s + "L";
        } else if ("Integer".equals(javaType) || "int".equals(javaType)) {
            return s;
        } else if ("BigDecimal".equals(javaType)) {
            return "new BigDecimal(\"" + s + "\")";
        } else if ("Boolean".equals(javaType) || "boolean".equals(javaType)) {
            return Boolean.parseBoolean(s) ? "true" : "false";
        } else if ("UUID".equals(javaType)) {
            return "UUID.fromString(\"" + escapeJava(s) + "\")";
        } else if ("LocalDateTime".equals(javaType)) {
            if ("now".equalsIgnoreCase(s) || "now()".equalsIgnoreCase(s)) {
                return "LocalDateTime.now()";
            }
            return "LocalDateTime.parse(\"" + escapeJava(s) + "\")";
        } else if ("LocalDate".equals(javaType)) {
            if ("now".equalsIgnoreCase(s) || "now()".equalsIgnoreCase(s)) {
                return "LocalDate.now()";
            }
            return "LocalDate.parse(\"" + escapeJava(s) + "\")";
        } else {
            // Enum or custom type
            if (s.contains(".")) {
                return s;
            }
            return javaType + "." + s;
        }
    }

    private static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
