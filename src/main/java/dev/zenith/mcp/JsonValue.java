package dev.zenith.mcp;

import java.util.*;

/**
 * Minimal RFC 8259 JSON parser and serializer written in pure Java.
 * Handles objects, arrays, strings, numbers, booleans, and nulls without any external dependencies.
 */
public class JsonValue {

    public enum Type {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }

    private final Type type;
    private final Object value;

    public JsonValue(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static JsonValue ofObject() {
        return new JsonValue(Type.OBJECT, new LinkedHashMap<String, JsonValue>());
    }

    public static JsonValue ofArray() {
        return new JsonValue(Type.ARRAY, new ArrayList<JsonValue>());
    }

    public static JsonValue ofString(String s) {
        return new JsonValue(Type.STRING, s);
    }

    public static JsonValue ofNumber(Number n) {
        return new JsonValue(Type.NUMBER, n);
    }

    public static JsonValue ofBoolean(boolean b) {
        return new JsonValue(Type.BOOLEAN, b);
    }

    public static JsonValue ofNull() {
        return new JsonValue(Type.NULL, null);
    }

    public Type getType() {
        return type;
    }

    public boolean isObject() { return type == Type.OBJECT; }
    public boolean isArray() { return type == Type.ARRAY; }
    public boolean isString() { return type == Type.STRING; }
    public boolean isNumber() { return type == Type.NUMBER; }
    public boolean isBoolean() { return type == Type.BOOLEAN; }
    public boolean isNull() { return type == Type.NULL; }

    @SuppressWarnings("unchecked")
    public Map<String, JsonValue> asObject() {
        if (type != Type.OBJECT) return Collections.emptyMap();
        return (Map<String, JsonValue>) value;
    }

    @SuppressWarnings("unchecked")
    public List<JsonValue> asArray() {
        if (type != Type.ARRAY) return Collections.emptyList();
        return (List<JsonValue>) value;
    }

    public String asString() {
        if (type == Type.STRING) return (String) value;
        return value != null ? value.toString() : "";
    }

    public int asInt(int defaultValue) {
        if (type == Type.NUMBER && value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public boolean asBoolean(boolean defaultValue) {
        if (type == Type.BOOLEAN && value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    public JsonValue put(String key, JsonValue val) {
        if (type == Type.OBJECT) {
            asObject().put(key, val != null ? val : ofNull());
        }
        return this;
    }

    public JsonValue put(String key, String val) {
        return put(key, val != null ? ofString(val) : ofNull());
    }

    public JsonValue put(String key, Number val) {
        return put(key, val != null ? ofNumber(val) : ofNull());
    }

    public JsonValue put(String key, boolean val) {
        return put(key, ofBoolean(val));
    }

    public JsonValue add(JsonValue val) {
        if (type == Type.ARRAY) {
            asArray().add(val != null ? val : ofNull());
        }
        return this;
    }

    public JsonValue add(String val) {
        return add(val != null ? ofString(val) : ofNull());
    }

    public JsonValue get(String key) {
        if (type == Type.OBJECT) {
            return asObject().get(key);
        }
        return null;
    }

    public String getString(String key, String def) {
        JsonValue val = get(key);
        if (val != null && val.isString()) return val.asString();
        return def;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        writeJson(sb);
        return sb.toString();
    }

    private void writeJson(StringBuilder sb) {
        switch (type) {
            case NULL:
                sb.append("null");
                break;
            case BOOLEAN:
                sb.append(value);
                break;
            case NUMBER:
                sb.append(value);
                break;
            case STRING:
                escapeString((String) value, sb);
                break;
            case ARRAY: {
                sb.append("[");
                List<JsonValue> list = asArray();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    list.get(i).writeJson(sb);
                }
                sb.append("]");
                break;
            }
            case OBJECT: {
                sb.append("{");
                Map<String, JsonValue> map = asObject();
                boolean first = true;
                for (Map.Entry<String, JsonValue> entry : map.entrySet()) {
                    if (!first) sb.append(",");
                    first = false;
                    escapeString(entry.getKey(), sb);
                    sb.append(":");
                    entry.getValue().writeJson(sb);
                }
                sb.append("}");
                break;
            }
        }
    }

    public static void escapeString(String s, StringBuilder sb) {
        if (s == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ==========================================
    // Parser Implementation
    // ==========================================
    public static JsonValue parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return ofNull();
        }
        return new Parser(json.trim()).parse();
    }

    private static class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        JsonValue parse() {
            skipWhitespace();
            JsonValue val = parseValue();
            skipWhitespace();
            return val;
        }

        private JsonValue parseValue() {
            skipWhitespace();
            if (pos >= src.length()) return ofNull();
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return ofString(parseString());
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            throw new IllegalArgumentException("Unexpected char at " + pos + ": '" + c + "'");
        }

        private JsonValue parseObject() {
            match('{');
            JsonValue obj = ofObject();
            skipWhitespace();
            if (peek() == '}') {
                match('}');
                return obj;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                match(':');
                JsonValue val = parseValue();
                obj.put(key, val);
                skipWhitespace();
                char next = peek();
                if (next == '}') {
                    match('}');
                    break;
                } else if (next == ',') {
                    match(',');
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at " + pos + " but got '" + next + "'");
                }
            }
            return obj;
        }

        private JsonValue parseArray() {
            match('[');
            JsonValue arr = ofArray();
            skipWhitespace();
            if (peek() == ']') {
                match(']');
                return arr;
            }
            while (true) {
                JsonValue val = parseValue();
                arr.add(val);
                skipWhitespace();
                char next = peek();
                if (next == ']') {
                    match(']');
                    break;
                } else if (next == ',') {
                    match(',');
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at " + pos + " but got '" + next + "'");
                }
            }
            return arr;
        }

        private String parseString() {
            match('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) break;
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 <= src.length()) {
                                String hex = src.substring(pos, pos + 4);
                                pos += 4;
                                sb.append((char) Integer.parseInt(hex, 16));
                            }
                            break;
                        default: sb.append(esc); break;
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private JsonValue parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return ofBoolean(true);
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return ofBoolean(false);
            }
            throw new IllegalArgumentException("Expected boolean at " + pos);
        }

        private JsonValue parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return ofNull();
            }
            throw new IllegalArgumentException("Expected null at " + pos);
        }

        private JsonValue parseNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            String numStr = src.substring(start, pos);
            if (isFloat) {
                return ofNumber(Double.parseDouble(numStr));
            } else {
                try {
                    return ofNumber(Long.parseLong(numStr));
                } catch (NumberFormatException e) {
                    return ofNumber(Double.parseDouble(numStr));
                }
            }
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private char peek() {
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        private void match(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at " + pos + " but found '" + peek() + "'");
            }
            pos++;
        }
    }
}
