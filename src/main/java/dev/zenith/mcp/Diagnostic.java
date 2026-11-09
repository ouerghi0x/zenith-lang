package dev.zenith.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Machine-readable compiler diagnostic matching MCP Spec 24 / Task 25.
 */
public class Diagnostic {
    private final String code;
    private final String severity; // ERROR, WARNING, INFO
    private final String file;
    private final int line;
    private final int column;
    private final String message;
    private final String suggestion;

    public Diagnostic(String code, String severity, String file, int line, int column, String message, String suggestion) {
        this.code = code != null ? code : "ZEN-000";
        this.severity = severity != null ? severity : "ERROR";
        this.file = file != null ? file : "schema.zen";
        this.line = Math.max(1, line);
        this.column = Math.max(1, column);
        this.message = message != null ? message : "";
        this.suggestion = suggestion;
    }

    public String getCode() {
        return code;
    }

    public String getSeverity() {
        return severity;
    }

    public String getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"code\":").append(quote(code)).append(",");
        sb.append("\"severity\":").append(quote(severity)).append(",");
        sb.append("\"file\":").append(quote(file)).append(",");
        sb.append("\"line\":").append(line).append(",");
        sb.append("\"column\":").append(column).append(",");
        sb.append("\"message\":").append(quote(message));
        if (suggestion != null) {
            sb.append(",\"suggestion\":").append(quote(suggestion));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String quote(String str) {
        if (str == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    public static String diagnosticsToJson(List<Diagnostic> diagnostics, boolean success) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"success\":").append(success).append(",");
        sb.append("\"diagnostics\":[");
        for (int i = 0; i < diagnostics.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(diagnostics.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }
}

