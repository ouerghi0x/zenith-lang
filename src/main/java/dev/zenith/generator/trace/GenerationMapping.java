package dev.zenith.generator.trace;

import dev.zenith.ast.SourceLocation;

/**
 * Represents a single bidirectional mapping record between:
 * - Emitted Java source file and line range
 * - Compiler Generator class, method, and internal line
 * - Original Zenith (.zen) source location
 */
public class GenerationMapping {
    private final String generatedFile;
    private final String generatedElement;
    private final int startLine;
    private final int endLine;
    private final String generatorClass;
    private final String generatorMethod;
    private final int generatorLine;
    private final SourceLocation sourceOrigin;
    private final String snippet;

    public GenerationMapping(String generatedFile, String generatedElement, int startLine, int endLine,
                             String generatorClass, String generatorMethod, int generatorLine,
                             SourceLocation sourceOrigin) {
        this(generatedFile, generatedElement, startLine, endLine, generatorClass, generatorMethod, generatorLine, sourceOrigin, null);
    }

    public GenerationMapping(String generatedFile, String generatedElement, int startLine, int endLine,
                             String generatorClass, String generatorMethod, int generatorLine,
                             SourceLocation sourceOrigin, String snippet) {
        this.generatedFile = generatedFile != null ? generatedFile.replace('\\', '/') : "";
        this.generatedElement = generatedElement;
        this.startLine = startLine;
        this.endLine = endLine;
        this.generatorClass = generatorClass;
        this.generatorMethod = generatorMethod;
        this.generatorLine = generatorLine;
        this.sourceOrigin = sourceOrigin;
        this.snippet = snippet;
    }

    public String getGeneratedFile() {
        return generatedFile;
    }

    public String getGeneratedElement() {
        return generatedElement;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public String getGeneratorClass() {
        return generatorClass;
    }

    public String getGeneratorMethod() {
        return generatorMethod;
    }

    public int getGeneratorLine() {
        return generatorLine;
    }

    public SourceLocation getSourceOrigin() {
        return sourceOrigin;
    }

    public String getSnippet() {
        return snippet;
    }

    public boolean coversLine(int line) {
        return line >= startLine && line <= endLine;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("      \"generatedFile\": \"").append(escape(generatedFile)).append("\",\n");
        sb.append("      \"generatedElement\": \"").append(escape(generatedElement)).append("\",\n");
        sb.append("      \"generatedLines\": { \"start\": ").append(startLine).append(", \"end\": ").append(endLine).append(" },\n");
        sb.append("      \"generator\": {\n");
        sb.append("        \"class\": \"").append(escape(generatorClass != null ? generatorClass : "unknown")).append("\",\n");
        sb.append("        \"method\": \"").append(escape(generatorMethod != null ? generatorMethod : "unknown")).append("\",\n");
        sb.append("        \"generatorLine\": ").append(generatorLine).append("\n");
        sb.append("      }");
        if (sourceOrigin != null) {
            sb.append(",\n      \"sourceOrigin\": {\n");
            sb.append("        \"file\": \"").append(escape(sourceOrigin.getSourceFile() != null ? sourceOrigin.getSourceFile().replace('\\', '/') : "")).append("\",\n");
            sb.append("        \"line\": ").append(sourceOrigin.getStartLine()).append(",\n");
            sb.append("        \"column\": ").append(sourceOrigin.getStartColumn()).append(",\n");
            sb.append("        \"snippet\": \"").append(escape(snippet != null ? snippet : "")).append("\"\n");
            sb.append("      }\n");
        } else {
            sb.append("\n");
        }
        sb.append("    }");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

