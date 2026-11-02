package dev.zenith.generator;

import dev.zenith.ast.ModelDef;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogicTranspiler {

    public static String transpileCondition(String expr, ModelDef model) {
        if (expr == null || expr.trim().isEmpty()) return "true";
        String s = expr.trim();

        // 0. Logical operator normalization (case-insensitive and/or -> &&/||)
        s = s.replaceAll("(?i)\\b(and)\\b", "&&");
        s = s.replaceAll("(?i)\\b(or)\\b", "||");

        // 1. entity prefix (e.g. status == ProductStatus.ACTIVE or entity.status == ...)
        String entityVar = "entity";
        String modelLower = model != null ? model.getName().toLowerCase() : "entity";

        // Convert body.field to body.field() for Java record accessor syntax
        s = s.replaceAll("\\bbody\\.([a-zA-Z0-9_]+)(?!\\()", "body.$1()");

        // Handle field references like `status ==` or `order.status ==` or `stock <= 0`
        if (model != null) {
            for (var field : model.getFields()) {
                String fName = field.getName();
                String cap = TypeHelper.capitalize(fName);

                // Replace entity.fName with entity.getCap()
                s = s.replaceAll("\\b" + entityVar + "\\." + fName + "\\b", "entity.get" + cap + "()");
                s = s.replaceAll("\\b" + modelLower + "\\." + fName + "\\b", "entity.get" + cap + "()");

                // If standalone field name appears: e.g. "status =="
                s = s.replaceAll("(?<![a-zA-Z0-9_.])" + fName + "(?![a-zA-Z0-9_])", "entity.get" + cap + "()");
            }
        }

        s = s.replaceAll("\\s*\\.\\s*", ".");
        return s;
    }

    public static String transpileStatement(String stmt, ModelDef model) {
        if (stmt == null || stmt.trim().isEmpty()) return "";
        String s = stmt.trim();

        // 1. Handle "create ModelName { ... }"
        if (s.startsWith("create ")) {
            return transpileCreateStatement(s, model);
        }

        // 2. Handle "update ModelName [on ...] { ... }"
        if (s.startsWith("update ")) {
            return transpileUpdateStatement(s, model);
        }

        // 3. Handle "notify(...)"
        if (s.matches("^notify\\s*\\(.*")) {
            return transpileNotifyStatement(s, model);
        }

        String entityVar = "entity";
        String modelLower = model != null ? model.getName().toLowerCase() : "entity";

        // Check if assignment: `status = ARCHIVED` or `entity.status = ProductStatus.ARCHIVED`
        if (s.contains("=") && !s.contains("==") && !s.contains("!=") && !s.contains("<=") && !s.contains(">=")) {
            String[] parts = s.split("=", 2);
            String left = parts[0].trim().replaceAll("\\s+", "");
            String right = parts[1].trim();

            if (left.startsWith(entityVar + ".")) {
                left = left.substring(entityVar.length() + 1).trim();
            } else if (left.startsWith(modelLower + ".")) {
                left = left.substring(modelLower.length() + 1).trim();
            }

            right = transpileValueExpression(right, model);

            // 1. Check if left side is a regular entity field
            if (model != null) {
                var field = model.findField(left);
                if (field != null) {
                    String cap = TypeHelper.capitalize(field.getName());
                    if ("now()".equalsIgnoreCase(right)) {
                        right = "java.time.LocalDateTime.now()";
                    }
                    return "entity.set" + cap + "(" + right + ");";
                }

                // 2. Check if left side is a relation
                var rel = model.findRelation(left);
                if (rel != null) {
                    String cap = TypeHelper.capitalize(rel.getFieldName());
                    if ("null".equalsIgnoreCase(right)) {
                        return "entity.set" + cap + "(null);";
                    }
                    String targetRepo = Character.toLowerCase(rel.getTargetModel().charAt(0))
                            + rel.getTargetModel().substring(1) + "Repository";
                    return "if (" + right + " != null) {\n"
                            + "    entity.set" + cap + "(" + targetRepo + ".findById(" + right + ")\n"
                            + "        .orElseThrow(() -> new ResourceNotFoundException(\"" + rel.getTargetModel()
                            + " not found with id: \" + " + right + ")));\n"
                            + "} else {\n"
                            + "    entity.set" + cap + "(null);\n"
                            + "}";
                }
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

    private static String transpileCreateStatement(String s, ModelDef model) {
        String rest = s.substring(7).trim();
        int braceIdx = rest.indexOf('{');
        if (braceIdx < 0) return "// " + s;
        String modelName = rest.substring(0, braceIdx).trim();
        String body = rest.substring(braceIdx + 1);
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1).trim();
        }

        String varName = Character.toLowerCase(modelName.charAt(0)) + modelName.substring(1);
        String repoVar = varName + "Repository";

        StringBuilder sb = new StringBuilder();
        sb.append(modelName).append(" ").append(varName).append(" = new ").append(modelName).append("();\n");

        List<String> assignments = splitTopLevelAssignments(body);
        for (String assign : assignments) {
            String[] kv = assign.split("=", 2);
            if (kv.length == 2) {
                String fieldName = kv[0].trim();
                String rawVal = kv[1].trim();
                String transVal = transpileValueExpression(rawVal, model);
                String setter = "set" + TypeHelper.capitalize(fieldName);
                sb.append(varName).append(".").append(setter).append("(").append(transVal).append(");\n");
            }
        }

        sb.append(repoVar).append(".save(").append(varName).append(");");
        return sb.toString();
    }

    private static String transpileNotifyStatement(String s, ModelDef model) {
        int lParen = s.indexOf('(');
        int rParen = s.lastIndexOf(')');
        if (lParen < 0 || rParen <= lParen) return "// " + s;
        String inside = s.substring(lParen + 1, rParen).trim();
        List<String> args = splitTopLevelAssignments(inside);
        if (args.isEmpty()) return "// " + s;
        String recipient = transpileValueExpression(args.get(0), model);
        String message = args.size() > 1 ? transpileValueExpression(args.get(1), model) : "\"Notification\"";
        return "// Event / Notification hook\n"
                + "org.slf4j.LoggerFactory.getLogger(getClass()).info(\"Notification to {}: {}\", "
                + recipient + ", " + message + ");";
    }

    private static String transpileUpdateStatement(String s, ModelDef model) {
        String rest = s.substring(7).trim();
        int braceIdx = rest.indexOf('{');
        if (braceIdx < 0) return "// " + s;
        String header = rest.substring(0, braceIdx).trim();
        String body = rest.substring(braceIdx + 1);
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1).trim();
        }

        String targetModel = header;
        if (header.contains(" on ")) {
            targetModel = header.substring(0, header.indexOf(" on ")).trim();
        }

        String varName = Character.toLowerCase(targetModel.charAt(0)) + targetModel.substring(1);
        String repoVar = varName + "Repository";

        List<String> items = splitTopLevelAssignments(body);
        String whereCond = null;
        List<String> mutations = new ArrayList<>();

        for (String item : items) {
            String trimmed = item.trim();
            if (trimmed.startsWith("where =") || trimmed.startsWith("where:")) {
                whereCond = trimmed.substring(trimmed.indexOf('=') >= 0 ? trimmed.indexOf('=') + 1 : trimmed.indexOf(':') + 1).trim();
            } else if (trimmed.startsWith("action =") || trimmed.startsWith("action:")) {
                String actBody = trimmed.substring(trimmed.indexOf('=') >= 0 ? trimmed.indexOf('=') + 1 : trimmed.indexOf(':') + 1).trim();
                if (actBody.startsWith("{") && actBody.endsWith("}")) {
                    actBody = actBody.substring(1, actBody.length() - 1).trim();
                }
                mutations.addAll(splitTopLevelAssignments(actBody));
            } else if (trimmed.contains("=")) {
                mutations.add(trimmed);
            }
        }

        String lookupId = "id";
        if (whereCond != null) {
            if (whereCond.contains("==")) {
                String[] parts = whereCond.split("==", 2);
                lookupId = parts[1].trim();
            } else if (whereCond.contains("=")) {
                String[] parts = whereCond.split("=", 2);
                lookupId = parts[1].trim();
            }
        }
        lookupId = transpileValueExpression(lookupId, model);

        StringBuilder sb = new StringBuilder();
        sb.append(repoVar).append(".findById(").append(lookupId).append(").ifPresent(target -> {\n");
        for (String mut : mutations) {
            String[] kv = mut.split("=", 2);
            if (kv.length == 2) {
                String f = kv[0].trim();
                String val = transpileValueExpression(kv[1].trim(), model);
                sb.append("    target.set").append(TypeHelper.capitalize(f)).append("(").append(val).append(");\n");
            }
        }
        sb.append("    ").append(repoVar).append(".save(target);\n");
        sb.append("});");
        return sb.toString();
    }

    public static String transpileValueExpression(String expr, ModelDef model) {
        if (expr == null || expr.trim().isEmpty()) return "null";
        String s = expr.trim();

        // 1. String literal extraction to prevent modifying contents inside quotes
        List<String> strings = new ArrayList<>();
        StringBuilder mask = new StringBuilder();
        boolean inQuotes = false;
        StringBuilder currentStr = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                if (!inQuotes) {
                    currentStr.append(c);
                    strings.add(currentStr.toString());
                    mask.append("___STR_").append(strings.size() - 1).append("___");
                    currentStr.setLength(0);
                } else {
                    currentStr.append(c);
                }
            } else if (inQuotes) {
                currentStr.append(c);
            } else {
                mask.append(c);
            }
        }
        String code = mask.toString();

        // Normalize dot spacing outside string literals so regex patterns match cleanly
        code = code.replaceAll("\\s*\\.\\s*", ".");

        // 2. Replace currentUser.email / currentUser.username / currentUser with currentUsername
        code = code.replaceAll("\\bcurrentUser\\.[a-zA-Z0-9_]+\\b", "currentUsername");
        code = code.replaceAll("\\bcurrentUser\\b", "currentUsername");

        // 3. Replace body.field with body.field() for Java record accessors
        code = code.replaceAll("\\bbody\\.([a-zA-Z0-9_]+)(?!\\()", "body.$1()");

        // 4. Replace now() with java.time.LocalDateTime.now()
        code = code.replaceAll("\\bnow\\(\\)", "java.time.LocalDateTime.now()");

        // 5. Transform entity.prop1.prop2 into entity.getProp1().getProp2()
        Pattern chainedPattern = Pattern.compile("\\bentity(?:\\.[a-zA-Z0-9_]+)+");
        Matcher m = chainedPattern.matcher(code);
        StringBuilder chainSb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            chainSb.append(code, lastEnd, m.start());
            String match = m.group();
            int nextCharIdx = m.end();
            while (nextCharIdx < code.length() && Character.isWhitespace(code.charAt(nextCharIdx))) {
                nextCharIdx++;
            }
            if (nextCharIdx < code.length() && code.charAt(nextCharIdx) == '(') {
                chainSb.append(match);
            } else {
                String[] parts = match.split("\\.");
                StringBuilder replacement = new StringBuilder(parts[0]);
                for (int pIdx = 1; pIdx < parts.length; pIdx++) {
                    replacement.append(".get").append(TypeHelper.capitalize(parts[pIdx])).append("()");
                }
                chainSb.append(replacement);
            }
            lastEnd = m.end();
        }
        chainSb.append(code.substring(lastEnd));
        code = chainSb.toString();

        // 6. Replace standalone model fields (e.g. title -> entity.getTitle())
        if (model != null) {
            for (var field : model.getFields()) {
                String fName = field.getName();
                String cap = TypeHelper.capitalize(fName);
                code = code.replaceAll("(?<![a-zA-Z0-9_.])" + fName + "(?![a-zA-Z0-9_])", "entity.get" + cap + "()");
            }
        }

        // 7. Restore string literals
        for (int i = 0; i < strings.size(); i++) {
            code = code.replace("___STR_" + i + "___", strings.get(i));
        }

        return code;
    }

    private static List<String> splitTopLevelAssignments(String body) {
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int parenDepth = 0;
        int braceDepth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (!inQuotes) {
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                else if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;

                if (c == ',' && parenDepth == 0 && braceDepth == 0) {
                    if (current.length() > 0 && !current.toString().trim().isEmpty()) {
                        list.add(current.toString().trim());
                        current.setLength(0);
                    }
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0 && !current.toString().trim().isEmpty()) {
            list.add(current.toString().trim());
        }
        return list;
    }
}
