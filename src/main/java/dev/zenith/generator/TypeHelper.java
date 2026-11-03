package dev.zenith.generator;

import dev.zenith.ast.FieldDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.TypeRef;

import java.util.ArrayList;
import java.util.List;

public class TypeHelper {

    public static String getDefaultLiteral(TypeRef type) {
        if (type == null) return "null";
        String simple = toSimpleJavaType(type);
        if ("Boolean".equalsIgnoreCase(simple) || "boolean".equalsIgnoreCase(simple)) return "false";
        if ("int".equalsIgnoreCase(simple) || "Integer".equalsIgnoreCase(simple)) return "0";
        if ("long".equalsIgnoreCase(simple) || "Long".equalsIgnoreCase(simple)) return "0L";
        if ("double".equalsIgnoreCase(simple) || "Double".equalsIgnoreCase(simple)) return "0.0";
        if ("BigDecimal".equalsIgnoreCase(simple) || "Decimal".equalsIgnoreCase(simple)) return "java.math.BigDecimal.ZERO";
        return "null";
    }

    public static String toJavaType(TypeRef type) {
        String name = type.getName();
        if (type.isList()) {
            return "java.util.List<" + toJavaType(TypeRef.simple(type.getGenericType())) + ">";
        }
        switch (name.toLowerCase()) {
            case "string":
            case "text":
                return "String";
            case "int":
            case "integer":
                return "Integer";
            case "long":
                return "Long";
            case "decimal":
            case "bigdecimal":
                return "java.math.BigDecimal";
            case "double":
                return "Double";
            case "float":
                return "Float";
            case "bool":
            case "boolean":
                return "Boolean";
            case "datetime":
            case "localdatetime":
                return "java.time.LocalDateTime";
            case "date":
            case "localdate":
                return "java.time.LocalDate";
            case "uuid":
                return "java.util.UUID";
            case "bytearray":
            case "bytes":
                return "byte[]";
            default:
                // Custom model or enum
                return name;
        }
    }

    public static String toSimpleJavaType(TypeRef type) {
        String full = toJavaType(type);
        int lastDot = full.lastIndexOf('.');
        return lastDot >= 0 ? full.substring(lastDot + 1) : full;
    }

    public static String toSqlColumnType(FieldDef field) {
        String typeName = field.getType().getName().toLowerCase();
        if (field.hasAnnotation("text")) {
            return "TEXT";
        }
        switch (typeName) {
            case "string":
                Object len = field.getType().getParams().get("length");
                if (len == null)
                    len = field.getType().getParams().get("max");
                return len != null ? "VARCHAR(" + len + ")" : "VARCHAR(255)";
            case "int":
            case "integer":
                return "INTEGER";
            case "long":
                return "BIGINT";
            case "decimal":
            case "bigdecimal":
                Object prec = field.getType().getParams().get("precision");
                Object scale = field.getType().getParams().get("scale");
                if (prec != null && scale != null) {
                    return "NUMERIC(" + prec + ", " + scale + ")";
                }
                return "NUMERIC(19, 4)";
            case "double":
                return "DOUBLE PRECISION";
            case "bool":
            case "boolean":
                return "BOOLEAN";
            case "datetime":
            case "localdatetime":
                return "TIMESTAMP";
            case "date":
            case "localdate":
                return "DATE";
            case "uuid":
                return "UUID";
            default:
                return "VARCHAR(255)";
        }
    }

    public static List<String> toValidationAnnotations(FieldDef field) {
        List<String> list = new ArrayList<>();
        String javaType = toSimpleJavaType(field.getType());

        if (!field.isOptional() && !field.hasAnnotation("id") && !field.hasAnnotation("auto")
                && !field.hasAnnotation("autoNow") && !field.hasAnnotation("autoUpdate")) {
            if ("String".equals(javaType)) {
                list.add("@jakarta.validation.constraints.NotBlank(message = \"" + field.getName()
                        + " cannot be blank\")");
            } else {
                list.add("@jakarta.validation.constraints.NotNull(message = \"" + field.getName()
                        + " cannot be null\")");
            }
        }

        // Check params min/max
        Object min = field.getType().getParams().get("min");
        Object max = field.getType().getParams().get("max");
        if ("String".equals(javaType) && (min != null || max != null)) {
            String mPart = min != null ? "min = " + min : "";
            String mxPart = max != null ? "max = " + max : "";
            String join = (!mPart.isEmpty() && !mxPart.isEmpty()) ? ", " : "";
            list.add("@jakarta.validation.constraints.Size(" + mPart + join + mxPart + ")");
        } else if (("Integer".equals(javaType) || "Long".equals(javaType)) && (min != null || max != null)) {
            if (min != null)
                list.add("@jakarta.validation.constraints.Min(" + min + ")");
            if (max != null)
                list.add("@jakarta.validation.constraints.Max(" + max + ")");
        }

        if (field.hasAnnotation("positive")) {
            list.add("@jakarta.validation.constraints.Positive");
        }
        if (field.hasAnnotation("positiveOrZero")) {
            list.add("@jakarta.validation.constraints.PositiveOrZero");
        }
        if (field.hasAnnotation("email")) {
            list.add("@jakarta.validation.constraints.Email");
        }
        if (field.hasAnnotation("min")) {
            Object val = field.getAnnotation("min").getArguments().getOrDefault("0",
                    field.getAnnotation("min").getArgument("value"));
            if (val != null) {
                list.add("@jakarta.validation.constraints.Min(" + val + ")");
            }
        }
        if (field.hasAnnotation("max")) {
            Object val = field.getAnnotation("max").getArguments().getOrDefault("0",
                    field.getAnnotation("max").getArgument("value"));
            if (val != null) {
                list.add("@jakarta.validation.constraints.Max(" + val + ")");
            }
        }

        return list;
    }

    public static String toSnakeCase(String str) {
        if (str == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0)
                    sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public static String pluralize(String str) {
        if (str == null || str.isEmpty())
            return str;
        if (str.endsWith("y") && !str.endsWith("ay") && !str.endsWith("ey") && !str.endsWith("oy")
                && !str.endsWith("uy")) {
            return str.substring(0, str.length() - 1) + "ies";
        }
        if (str.endsWith("s") || str.endsWith("x") || str.endsWith("z") || str.endsWith("ch") || str.endsWith("sh")) {
            return str + "es";
        }
        return str + "s";
    }

    public static String toEndpointMethodName(String httpMethod, String path) {
        String cleaned = path.replaceAll("\\{[^}]+\\}", "");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9]+", " ").trim();
        if (cleaned.isEmpty()) {
            return httpMethod.toLowerCase();
        }
        String[] parts = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            sb.append(capitalize(parts[i].toLowerCase()));
        }
        return sb.toString();
    }

    public static class PathVariable {
        private final String name;
        private final String type;

        public PathVariable(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    public static String mapTypeName(String rawType) {
        if (rawType == null || rawType.trim().isEmpty())
            return "String";
        String t = rawType.trim();
        switch (t.toLowerCase()) {
            case "string":
            case "text":
                return "String";
            case "uuid":
                return "UUID";
            case "int":
            case "integer":
                return "Integer";
            case "long":
                return "Long";
            case "decimal":
            case "bigdecimal":
                return "BigDecimal";
            case "double":
                return "Double";
            case "float":
                return "Float";
            case "boolean":
            case "bool":
                return "Boolean";
            case "datetime":
                return "LocalDateTime";
            case "date":
                return "LocalDate";
            default:
                return t;
        }
    }

    public static String sanitizePath(String path) {
        if (path == null)
            return "/";
        return path.replaceAll("\\{\\s*([a-zA-Z0-9_]+)\\s*:[^}]+\\}", "{$1}");
    }

    public static List<PathVariable> extractPathVariables(String path, ModelDef model, String idType,
            dev.zenith.ast.CustomEndpointDef custom) {
        List<PathVariable> list = new ArrayList<>();
        if (path == null)
            return list;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(path);
        while (m.find()) {
            String raw = m.group(1).trim();
            String varName = raw;
            String explicitType = null;

            if (raw.contains(":")) {
                String[] parts = raw.split(":", 2);
                varName = parts[0].trim();
                explicitType = mapTypeName(parts[1].trim());
            }

            if (explicitType == null && custom != null && custom.getParamTypes() != null) {
                if (custom.getParamTypes().containsKey(varName)) {
                    explicitType = mapTypeName(custom.getParamTypes().get(varName));
                }
            }

            String type = explicitType;

            if (type == null && custom != null && custom.getQuery() != null && !custom.getQuery().isEmpty()) {
                String q = custom.getQuery().replaceAll("\\s+", "");
                if (q.contains("==")) {
                    String[] parts = q.split("==");
                    String fieldName = parts[0].trim();
                    String paramName = parts[1].trim();
                    if (varName.equalsIgnoreCase(paramName)) {
                        if ("id".equalsIgnoreCase(fieldName)) {
                            type = idType;
                        } else if (model != null && model.findField(fieldName) != null) {
                            FieldDef f = model.findField(fieldName);
                            type = f.hasAnnotation("id") ? idType : toSimpleJavaType(f.getType());
                        }
                    }
                }
            }

            if (type == null) {
                if ("id".equalsIgnoreCase(varName)) {
                    type = idType;
                } else if (model != null && model.findField(varName) != null) {
                    FieldDef f = model.findField(varName);
                    type = f.hasAnnotation("id") ? idType : toSimpleJavaType(f.getType());
                } else if (model != null) {
                    for (dev.zenith.ast.RelationDef r : model.getRelations()) {
                        String rName = r.getFieldName().toLowerCase();
                        String vLower = varName.toLowerCase();
                        if (vLower.equals(rName) || vLower.equals(rName + "id") || vLower.equals("id" + rName)) {
                            type = "Long";
                            break;
                        }
                    }
                }
                if (type == null) {
                    type = "String";
                }
            }
            list.add(new PathVariable(varName, type));
        }
        return list;
    }

    public static List<PathVariable> extractPathVariables(String path, ModelDef model, String idType) {
        return extractPathVariables(path, model, idType, null);
    }

    public static String toVariableName(String bodyType) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toVariableName'");
    }
}
