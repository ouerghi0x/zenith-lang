package dev.zenith.generator;

import dev.zenith.ast.DtoDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return transpileStatement(stmt, model, null);
    }

    public static String transpileStatement(String stmt, ModelDef model, ZenithFile file) {
        if (stmt == null || stmt.trim().isEmpty()) return "";
        String s = stmt.trim();

        // 0. Handle "var <varName> = create/update ..."
        if (s.startsWith("var ")) {
            return transpileVarBindingStatement(s, model, file);
        }

        // 1. Handle "create ModelName { ... }"
        if (s.startsWith("create ")) {
            return transpileCreateStatement(s, model);
        }

        // 2. Handle "update ModelName [on ...] { ... }"
        if (s.startsWith("update ")) {
            return transpileUpdateStatement(s, model, file);
        }

        // 3. Handle "notify(...)"
        if (s.matches("^notify\\s*\\(.*")) {
            return transpileNotifyStatement(s, model);
        }

        // 3.5. Handle "send MailName(...)"
        if (s.startsWith("send ") || s.matches("^send\\s+[A-Za-z0-9_]+\\s*\\(.*")) {
            return transpileSendStatement(s, model, file);
        }

        // 4. Handle "return DTO { ... }" or "return expr;"
        if (s.startsWith("return ") || s.equals("return")) {
            return transpileReturnStatement(s, model, file);
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
                    String fType = TypeHelper.toSimpleJavaType(field.getType());
                    if ("LocalDate".equalsIgnoreCase(fType) || "Date".equalsIgnoreCase(field.getType().getName())) {
                        if (right.contains("java.time.LocalDateTime.now()")) {
                            right = right.replace("java.time.LocalDateTime.now()", "java.time.LocalDate.now()");
                        }
                    }
                    if ("now()".equalsIgnoreCase(right)) {
                        right = ("LocalDate".equalsIgnoreCase(fType) || "Date".equalsIgnoreCase(field.getType().getName()))
                                ? "java.time.LocalDate.now()"
                                : "java.time.LocalDateTime.now()";
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

                throw new dev.zenith.analyzer.AnalysisException(
                        "Cannot assign to unknown field or relation '" + left + "' on model '" + model.getName() + "'.");
            }
        }

        // Check if return
        if (s.startsWith("return ") || s.equals("return")) {
            return transpileReturnStatement(s, model, file);
        }

        throw new dev.zenith.analyzer.AnalysisException("Unsupported action statement or expression: '" + s + "'. "
                + "Zenith action statements must be an assignment ('field = value'), 'create Model { ... }', 'update Model { ... }', 'notify(to, message)', or 'return ...'.");
    }

    private static String transpileVarBindingStatement(String s, ModelDef model, ZenithFile file) {
        String rest = s.substring(4).trim(); // skip "var "
        int eqIdx = rest.indexOf('=');
        if (eqIdx < 0) return "// " + s;
        String boundVar = rest.substring(0, eqIdx).trim();
        String rhs = rest.substring(eqIdx + 1).trim();

        if (rhs.startsWith("create ")) {
            return transpileCreateStatement(rhs, model, file, boundVar);
        } else if (rhs.startsWith("update ")) {
            return transpileUpdateStatement(rhs, model, file, boundVar);
        }

        return boundVar + " = " + transpileValueExpression(rhs, model) + ";";
    }

    private static String transpileCreateStatement(String s, ModelDef model) {
        return transpileCreateStatement(s, model, null, null);
    }

    private static String transpileCreateStatement(String s, ModelDef model, ZenithFile file, String explicitVarName) {
        String rest = s.substring(7).trim();
        int braceIdx = rest.indexOf('{');
        if (braceIdx < 0) return "// " + s;
        String modelName = rest.substring(0, braceIdx).trim();
        String body = rest.substring(braceIdx + 1);
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1).trim();
        }

        ModelDef targetModelDef = file != null ? file.findModel(modelName) : null;
        if (targetModelDef == null && model != null && model.getName().equalsIgnoreCase(modelName)) {
            targetModelDef = model;
        }

        String varName = explicitVarName != null ? explicitVarName : (Character.toLowerCase(modelName.charAt(0)) + modelName.substring(1));
        String repoVar = Character.toLowerCase(modelName.charAt(0)) + modelName.substring(1) + "Repository";

        StringBuilder sb = new StringBuilder();
        sb.append(modelName).append(" ").append(varName).append(" = new ").append(modelName).append("();\n");

        List<String> assignments = splitTopLevelAssignments(body);
        for (String assign : assignments) {
            String[] kv = assign.split("=", 2);
            if (kv.length == 2) {
                String fieldName = kv[0].trim();
                String rawVal = kv[1].trim();
                String transVal = transpileValueExpression(rawVal, model, file);
                transVal = qualifyEnumValue(fieldName, transVal, targetModelDef, file);
                String setter = "set" + TypeHelper.capitalize(fieldName);
                sb.append(varName).append(".").append(setter).append("(").append(transVal).append(");\n");
            }
        }

        if (explicitVarName != null) {
            sb.append(varName).append(" = ").append(repoVar).append(".save(").append(varName).append(");");
        } else {
            sb.append(repoVar).append(".save(").append(varName).append(");");
        }
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

    private static String transpileReturnStatement(String s, ModelDef model, ZenithFile file) {
        String trimmed = s.substring(6).trim(); // skip "return"
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return "return;";
        }
        int lBrace = trimmed.indexOf('{');
        int rBrace = trimmed.lastIndexOf('}');
        if (lBrace > 0 && rBrace > lBrace) {
            String dtoName = trimmed.substring(0, lBrace).trim();
            String body = trimmed.substring(lBrace + 1, rBrace).trim();
            List<String> items = splitTopLevelAssignments(body);
            Map<String, String> values = new LinkedHashMap<>();
            for (String item : items) {
                String[] kv = item.split("=", 2);
                if (kv.length == 2) {
                    values.put(kv[0].trim().toLowerCase(), transpileValueExpression(kv[1].trim(), model));
                }
            }

            DtoDef dto = file != null ? file.findDto(dtoName) : null;
            StringBuilder sb = new StringBuilder();
            sb.append("return new ").append(dtoName).append("(\n");
            List<String> args = new ArrayList<>();
            if (dto != null) {
                for (var f : dto.getFields()) {
                    String fLow = f.getName().toLowerCase();
                    if (values.containsKey(fLow)) {
                        args.add("    " + values.get(fLow));
                    } else if (model != null && model.findField(f.getName()) != null) {
                        args.add("    entity.get" + TypeHelper.capitalize(f.getName()) + "()");
                    } else if (model != null && f.getName().endsWith("Id") && model.findRelation(f.getName().substring(0, f.getName().length() - 2)) != null) {
                        String relName = f.getName().substring(0, f.getName().length() - 2);
                        args.add("    entity.get" + TypeHelper.capitalize(relName) + "() != null ? entity.get" + TypeHelper.capitalize(relName) + "().getId() : null");
                    } else {
                        args.add("    " + TypeHelper.getDefaultLiteral(f.getType()));
                    }
                }
            } else {
                for (String val : values.values()) {
                    args.add("    " + val);
                }
            }
            sb.append(String.join(",\n", args));
            sb.append("\n);");
            return sb.toString();
        }

        return "return " + transpileValueExpression(trimmed, model) + ";";
    }

    private static String transpileUpdateStatement(String s, ModelDef model, ZenithFile file) {
        return transpileUpdateStatement(s, model, file, null);
    }

    private static String transpileUpdateStatement(String s, ModelDef model, ZenithFile file, String explicitVarName) {
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

        ModelDef targetModelDef = null;
        if (file != null) {
            targetModelDef = file.findModel(targetModel);
        }
        if (targetModelDef == null && model != null && model.getName().equalsIgnoreCase(targetModel)) {
            targetModelDef = model;
        }

        String varName = Character.toLowerCase(targetModel.charAt(0)) + targetModel.substring(1);
        String repoVar = varName + "Repository";

        List<String> items = splitTopLevelAssignments(body);
        String whereCond = null;
        List<String> mutations = new ArrayList<>();

        for (String item : items) {
            String trimmed = item.trim();
            if (trimmed.matches("^(?i)where\\s*[:=].*")) {
                int colonIdx = trimmed.indexOf(':');
                int eqIdx = trimmed.indexOf('=');
                int sepIdx = (colonIdx >= 0 && (eqIdx < 0 || colonIdx < eqIdx)) ? colonIdx : eqIdx;
                whereCond = trimmed.substring(sepIdx + 1).trim();
            } else if (trimmed.matches("^(?i)action\\s*[:=].*")) {
                int colonIdx = trimmed.indexOf(':');
                int eqIdx = trimmed.indexOf('=');
                int sepIdx = (colonIdx >= 0 && (eqIdx < 0 || colonIdx < eqIdx)) ? colonIdx : eqIdx;
                String actBody = trimmed.substring(sepIdx + 1).trim();
                if (actBody.startsWith("{") && actBody.endsWith("}")) {
                    actBody = actBody.substring(1, actBody.length() - 1).trim();
                }
                mutations.addAll(splitTopLevelAssignments(actBody));
            } else if (trimmed.contains("=")) {
                mutations.add(trimmed);
            }
        }

        boolean isSingleEntity = whereCond != null && (whereCond.trim().startsWith("id =") || whereCond.trim().startsWith("id==") || whereCond.trim().startsWith("id "));
        if (whereCond == null && header.contains(" on ")) {
            isSingleEntity = true;
        }

        StringBuilder sb = new StringBuilder();
        if (explicitVarName != null) {
            // When bound to a variable, we must resolve to a single entity instance
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
            lookupId = transpileValueExpression(lookupId, targetModelDef != null ? targetModelDef : model);

            sb.append(targetModel).append(" ").append(explicitVarName).append(" = ").append(repoVar).append(".findById(").append(lookupId).append(")\n");
            sb.append("        .orElseThrow(() -> new ResourceNotFoundException(\"").append(targetModel).append(" not found with id: \" + ").append(lookupId).append("));\n");
            for (String mut : mutations) {
                String[] kv = mut.split("=", 2);
                if (kv.length == 2) {
                    String f = kv[0].trim();
                    String val = transpileValueExpression(kv[1].trim(), targetModelDef != null ? targetModelDef : model, file);
                    val = qualifyEnumValue(f, val, targetModelDef, file);
                    sb.append(explicitVarName).append(".set").append(TypeHelper.capitalize(f)).append("(").append(val).append(");\n");
                }
            }
            sb.append(explicitVarName).append(" = ").append(repoVar).append(".save(").append(explicitVarName).append(");");
            return sb.toString();
        }

        if (isSingleEntity || (whereCond != null && !whereCond.contains("and") && !whereCond.contains("or") && !whereCond.contains("<") && !whereCond.contains(">") && whereCond.contains("id"))) {
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
            lookupId = transpileValueExpression(lookupId, targetModelDef != null ? targetModelDef : model);

            sb.append(repoVar).append(".findById(").append(lookupId).append(").ifPresent(target -> {\n");
            for (String mut : mutations) {
                String[] kv = mut.split("=", 2);
                if (kv.length == 2) {
                    String f = kv[0].trim();
                    String val = transpileValueExpression(kv[1].trim(), targetModelDef != null ? targetModelDef : model, file);
                    val = qualifyEnumValue(f, val, targetModelDef, file);
                    sb.append("    target.set").append(TypeHelper.capitalize(f)).append("(").append(val).append(");\n");
                }
            }
            sb.append("    ").append(repoVar).append(".save(target);\n");
            sb.append("});");
        } else {
            String filter = transpileFilterCondition(whereCond, targetModel, file);
            sb.append(repoVar).append(".findAll().stream()\n");
            if (whereCond != null && !whereCond.trim().isEmpty()) {
                sb.append("    .filter(target -> ").append(filter).append(")\n");
            }
            sb.append("    .forEach(target -> {\n");
            for (String mut : mutations) {
                String[] kv = mut.split("=", 2);
                if (kv.length == 2) {
                    String f = kv[0].trim();
                    String val = transpileValueExpression(kv[1].trim(), targetModelDef != null ? targetModelDef : model, file);
                    val = qualifyEnumValue(f, val, targetModelDef, file);
                    sb.append("        target.set").append(TypeHelper.capitalize(f)).append("(").append(val).append(");\n");
                }
            }
            sb.append("        ").append(repoVar).append(".save(target);\n");
            sb.append("    });");
        }
        return sb.toString();
    }

    private static String qualifyEnumValue(String fieldName, String value, ModelDef targetModelDef, ZenithFile file) {
        if (value == null || value.contains(".") || !value.matches("^[A-Z0-9_]+$")) {
            return value;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("null")) {
            return value;
        }
        if (targetModelDef != null) {
            var field = targetModelDef.findField(fieldName);
            if (field != null && file != null) {
                for (var en : file.getEnums()) {
                    if (en.getName().equalsIgnoreCase(field.getType().getName()) || en.getValues().contains(value)) {
                        return en.getName() + "." + value;
                    }
                }
            }
        }
        if (file != null) {
            for (var en : file.getEnums()) {
                if (en.getValues().contains(value)) {
                    return en.getName() + "." + value;
                }
            }
        }
        return value;
    }

    private static String transpileSendStatement(String s, ModelDef model, ZenithFile file) {
        // e.g. send OrderConfirmation(order = entity, customer = entity.customer)
        int parenOpen = s.indexOf('(');
        int parenClose = s.lastIndexOf(')');
        if (parenOpen < 0 || parenClose < parenOpen) {
            return "// malformed send statement: " + s;
        }
        String mailName = s.substring(4, parenOpen).trim();
        String argsBlock = s.substring(parenOpen + 1, parenClose).trim();

        dev.zenith.ast.MailDef mailDef = file != null ? file.findMail(mailName) : null;
        Map<String, String> argMap = new LinkedHashMap<>();
        if (!argsBlock.isEmpty()) {
            String[] parts = argsBlock.split(",");
            for (String part : parts) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                String[] kv = p.split("=", 2);
                if (kv.length == 2) {
                    argMap.put(kv[0].trim().toLowerCase(), kv[1].trim());
                } else {
                    argMap.put(kv[0].trim().toLowerCase(), kv[0].trim());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("emailService.send").append(mailName).append("(");
        if (mailDef != null) {
            int idx = 0;
            for (var param : mailDef.getParameters()) {
                if (idx > 0) sb.append(", ");
                String rawArg = argMap.get(param.getName().toLowerCase());
                if (rawArg == null) rawArg = param.getName();
                String transpiledArg = transpileValueExpression(rawArg, model, file);
                sb.append(transpiledArg);
                idx++;
            }
        } else {
            // Fallback if mailDef not resolved in isolated unit test
            int idx = 0;
            for (Map.Entry<String, String> entry : argMap.entrySet()) {
                if (idx > 0) sb.append(", ");
                sb.append(transpileValueExpression(entry.getValue(), model, file));
                idx++;
            }
        }
        sb.append(");");
        return sb.toString();
    }

    public static String normalizeDateOperations(String code) {
        if (code == null) return null;
        String s = code;
        s = s.replaceAll("(?<!\\.)\\bnow\\s*\\(\\s*\\)", "java.time.LocalDateTime.now()");
        s = s.replaceAll("(?<!\\.)\\btoday\\s*\\(\\s*\\)", "java.time.LocalDate.now()");

        s = s.replaceAll("\\bdaysFromNow\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().plusDays($1)");
        s = s.replaceAll("\\bhoursFromNow\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().plusHours($1)");
        s = s.replaceAll("\\bminutesFromNow\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().plusMinutes($1)");
        s = s.replaceAll("\\bmonthsFromNow\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().plusMonths($1)");
        s = s.replaceAll("\\byearsFromNow\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().plusYears($1)");

        s = s.replaceAll("\\bdaysAgo\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().minusDays($1)");
        s = s.replaceAll("\\bhoursAgo\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().minusHours($1)");
        s = s.replaceAll("\\bminutesAgo\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().minusMinutes($1)");
        s = s.replaceAll("\\bmonthsAgo\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().minusMonths($1)");
        s = s.replaceAll("\\byearsAgo\\s*\\(\\s*([^)]+)\\s*\\)", "java.time.LocalDateTime.now().minusYears($1)");

        s = s.replaceAll("\\b([a-zA-Z0-9_]+)\\.daysFromNow\\b", "java.time.LocalDateTime.now().plusDays($1)");
        s = s.replaceAll("\\b([a-zA-Z0-9_]+)\\.hoursFromNow\\b", "java.time.LocalDateTime.now().plusHours($1)");
        s = s.replaceAll("\\b([a-zA-Z0-9_]+)\\.minutesFromNow\\b", "java.time.LocalDateTime.now().plusMinutes($1)");
        s = s.replaceAll("\\b([a-zA-Z0-9_]+)\\.daysAgo\\b", "java.time.LocalDateTime.now().minusDays($1)");
        s = s.replaceAll("\\b([a-zA-Z0-9_]+)\\.hoursAgo\\b", "java.time.LocalDateTime.now().minusHours($1)");
        s = s.replaceAll("\\b([a-zA-Z0-9_]+)\\.minutesAgo\\b", "java.time.LocalDateTime.now().minusMinutes($1)");

        s = s.replace("java.time.LocalDateTime.java.time.LocalDateTime", "java.time.LocalDateTime");
        s = s.replace("java.time.LocalDate.java.time.LocalDate", "java.time.LocalDate");
        return s;
    }

    public static String transpileFilterCondition(String cond, String targetModel) {
        return transpileFilterCondition(cond, targetModel, null);
    }

    public static String transpileFilterCondition(String cond, String targetModel, ZenithFile file) {
        if (cond == null || cond.trim().isEmpty()) return "true";
        String s = cond.trim();
        s = s.replaceAll("(?i)\\b(and)\\b", "&&");
        s = s.replaceAll("(?i)\\b(or)\\b", "||");
        s = normalizeDateOperations(s);

        // Convert `var <= java.time` to `(!var.isAfter(...))`
        Pattern ltePattern = Pattern.compile("([a-zA-Z0-9_.]+)\\s*<=\\s*(java\\.time\\.[^&|]+)");
        Matcher lteMatcher = ltePattern.matcher(s);
        StringBuffer lteSb = new StringBuffer();
        while (lteMatcher.find()) {
            String left = lteMatcher.group(1);
            String right = lteMatcher.group(2).trim();
            if (!left.startsWith("target.")) {
                left = "target.get" + TypeHelper.capitalize(left) + "()";
            }
            lteMatcher.appendReplacement(lteSb, Matcher.quoteReplacement("(" + left + " != null && !" + left + ".isAfter(" + right + "))"));
        }
        lteMatcher.appendTail(lteSb);
        s = lteSb.toString();

        // Convert `var >= java.time` to `(!var.isBefore(...))`
        Pattern gtePattern = Pattern.compile("([a-zA-Z0-9_.]+)\\s*>=\\s*(java\\.time\\.[^&|]+)");
        Matcher gteMatcher = gtePattern.matcher(s);
        StringBuffer gteSb = new StringBuffer();
        while (gteMatcher.find()) {
            String left = gteMatcher.group(1);
            String right = gteMatcher.group(2).trim();
            if (!left.startsWith("target.")) {
                left = "target.get" + TypeHelper.capitalize(left) + "()";
            }
            gteMatcher.appendReplacement(gteSb, Matcher.quoteReplacement("(" + left + " != null && !" + left + ".isBefore(" + right + "))"));
        }
        gteMatcher.appendTail(gteSb);
        s = gteSb.toString();

        // Convert `var < java.time` to `var.isBefore(...)`
        Pattern ltPattern = Pattern.compile("([a-zA-Z0-9_.]+)\\s*<\\s*(java\\.time\\.[^&|]+)");
        Matcher ltMatcher = ltPattern.matcher(s);
        StringBuffer ltSb = new StringBuffer();
        while (ltMatcher.find()) {
            String left = ltMatcher.group(1);
            String right = ltMatcher.group(2).trim();
            if (!left.startsWith("target.")) {
                left = "target.get" + TypeHelper.capitalize(left) + "()";
            }
            ltMatcher.appendReplacement(ltSb, Matcher.quoteReplacement("(" + left + " != null && " + left + ".isBefore(" + right + "))"));
        }
        ltMatcher.appendTail(ltSb);
        s = ltSb.toString();

        // Convert `var > java.time` to `var.isAfter(...)`
        Pattern gtPattern = Pattern.compile("([a-zA-Z0-9_.]+)\\s*>\\s*(java\\.time\\.[^&|]+)");
        Matcher gtMatcher = gtPattern.matcher(s);
        StringBuffer gtSb = new StringBuffer();
        while (gtMatcher.find()) {
            String left = gtMatcher.group(1);
            String right = gtMatcher.group(2).trim();
            if (!left.startsWith("target.")) {
                left = "target.get" + TypeHelper.capitalize(left) + "()";
            }
            gtMatcher.appendReplacement(gtSb, Matcher.quoteReplacement("(" + left + " != null && " + left + ".isAfter(" + right + "))"));
        }
        gtMatcher.appendTail(gtSb);
        s = gtSb.toString();

        // Handle target.status == DONE or status == DONE
        Pattern eqPattern = Pattern.compile("(?<![a-zA-Z0-9_.])([a-zA-Z0-9_]+)\\s*(==|!=)\\s*([A-Z0-9_]+)(?![a-zA-Z0-9_])");
        Matcher eqMatcher = eqPattern.matcher(s);
        StringBuffer eqSb = new StringBuffer();
        while (eqMatcher.find()) {
            String field = eqMatcher.group(1);
            String op = eqMatcher.group(2);
            String val = eqMatcher.group(3);
            if (!val.equalsIgnoreCase("null") && !val.equalsIgnoreCase("true") && !val.equalsIgnoreCase("false")) {
                String getter = "target.get" + TypeHelper.capitalize(field) + "()";
                String check = op.equals("==")
                    ? "(" + getter + " != null && \"" + val + "\".equalsIgnoreCase(" + getter + ".name()))"
                    : "(" + getter + " == null || !\"" + val + "\".equalsIgnoreCase(" + getter + ".name()))";
                eqMatcher.appendReplacement(eqSb, Matcher.quoteReplacement(check));
            }
        }
        eqMatcher.appendTail(eqSb);
        s = eqSb.toString();
        s = s.replace("java.time.LocalDateTime.java.time.LocalDateTime", "java.time.LocalDateTime");

        return s;
    }

    public static String transpileValueExpression(String expr, ModelDef model) {
        return transpileValueExpression(expr, model, null);
    }

    public static String transpileValueExpression(String expr, ModelDef model, ZenithFile file) {
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

        // 4. Normalize date operations (now, today, daysAgo, daysFromNow, etc.)
        code = normalizeDateOperations(code);

        // 5. Transform entity.prop1.prop2 into entity.getProp1().getProp2()
        // and any boundVar.prop1 into boundVar.getProp1()
        Pattern chainedPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?:\\.[a-zA-Z0-9_]+)+");
        Matcher m = chainedPattern.matcher(code);
        StringBuilder chainSb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            chainSb.append(code, lastEnd, m.start());
            String match = m.group();
            String root = match.split("\\.")[0];
            // Skip java standard packages/classes or declared enums
            boolean isEnumName = false;
            if (file != null) {
                for (var en : file.getEnums()) {
                    if (en.getName().equalsIgnoreCase(root)) {
                        isEnumName = true;
                        break;
                    }
                }
            }
            if (isEnumName || root.equals("java") || root.equals("org") || root.equals("com") || root.equals("Math") || root.equals("System")) {
                chainSb.append(match);
                lastEnd = m.end();
                continue;
            }
            int nextCharIdx = m.end();
            while (nextCharIdx < code.length() && Character.isWhitespace(code.charAt(nextCharIdx))) {
                nextCharIdx++;
            }
            if (nextCharIdx < code.length() && code.charAt(nextCharIdx) == '(') {
                String[] parts = match.split("\\.");
                if (parts.length > 2) {
                    StringBuilder replacement = new StringBuilder(parts[0]);
                    for (int pIdx = 1; pIdx < parts.length - 1; pIdx++) {
                        replacement.append(".get").append(TypeHelper.capitalize(parts[pIdx])).append("()");
                    }
                    replacement.append(".").append(parts[parts.length - 1]);
                    chainSb.append(replacement);
                } else {
                    chainSb.append(match);
                }
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
