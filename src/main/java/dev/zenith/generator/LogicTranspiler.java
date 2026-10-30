package dev.zenith.generator;

import dev.zenith.ast.ModelDef;

public class LogicTranspiler {

    public static String transpileCondition(String expr, ModelDef model) {
        if (expr == null || expr.trim().isEmpty()) return "true";
        String s = expr.trim();

        // 0. Logical operator normalization (case-insensitive and/or -> &&/||)
        s = s.replaceAll("(?i)\\b(and)\\b", "&&");
        s = s.replaceAll("(?i)\\b(or)\\b", "||");

        // 1. entity prefix (e.g. status == ProductStatus.ACTIVE or entity.status == ...)
        String entityVar = "entity";
        String modelLower = model.getName().toLowerCase();

        // Handle field references like `status ==` or `order.status ==` or `stock <= 0`
        // Replace `modelLower.` or `entity.` with `entity.get<Field>()`
        for (var field : model.getFields()) {
            String fName = field.getName();
            String cap = TypeHelper.capitalize(fName);

            // Replace entity.fName with entity.getCap()
            s = s.replaceAll("\\b" + entityVar + "\\." + fName + "\\b", "entity.get" + cap + "()");
            s = s.replaceAll("\\b" + modelLower + "\\." + fName + "\\b", "entity.get" + cap + "()");

            // If standalone field name appears: e.g. "status =="
            s = s.replaceAll("(?<![a-zA-Z0-9_.])" + fName + "(?![a-zA-Z0-9_])", "entity.get" + cap + "()");
        }

        s = s.replaceAll("\\s*\\.\\s*", ".");
        return s;
    }

    public static String transpileStatement(String stmt, ModelDef model) {
        if (stmt == null || stmt.trim().isEmpty()) return "";
        String s = stmt.trim().replaceAll("\\s*\\.\\s*", ".");

        String entityVar = "entity";
        String modelLower = model.getName().toLowerCase();

        // Check if assignment: `status = ARCHIVED` or `entity.status = ProductStatus.ARCHIVED`
        if (s.contains("=") && !s.contains("==") && !s.contains("!=") && !s.contains("<=") && !s.contains(">=")) {
            String[] parts = s.split("=", 2);
            String left = parts[0].trim();
            String right = parts[1].trim();

            if (left.startsWith(entityVar + ".")) {
                left = left.substring(entityVar.length() + 1).trim();
            } else if (left.startsWith(modelLower + ".")) {
                left = left.substring(modelLower.length() + 1).trim();
            }

            var field = model.findField(left);
            if (field != null) {
                String cap = TypeHelper.capitalize(field.getName());
                // Handle right side helpers like now()
                if ("now()".equalsIgnoreCase(right)) {
                    right = "java.time.LocalDateTime.now()";
                }
                return "entity.set" + cap + "(" + right + ");";
            }
        }

        // Check if return
        if (s.startsWith("return ")) {
            return "// " + s;
        }

        if (!s.endsWith(";")) {
            s += ";";
        }
        return s;
    }
}
