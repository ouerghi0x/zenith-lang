package dev.zenith.diagnostics;

import dev.zenith.ast.SourceLocation;
import dev.zenith.generator.trace.GenerationMapping;

/**
 * Standardized diagnostic DTO representing a compiler or build failure,
 * linked bidirectionally to Java target, Zenith compiler generator, and Zenith source schema.
 */
public class CompilerDiagnostic {

    public enum ErrorCategory {
        GENERATOR_DEFECT,        // Zenith bug / missing import / bad generated syntax
        SPECIFICATION_DEFECT,    // User schema issue / type incompatibility / missing relation
        ENVIRONMENT_DEFECT,      // JDK release mismatch, JVM heap, missing maven
        EXTERNAL_DEPENDENCY      // Network failure, missing remote repository / dependency
    }

    private final String rawMessage;
    private final String generatedFile;
    private final int generatedLine;
    private final int generatedColumn;
    private final String javacError;
    private final ErrorCategory category;

    // Origin Generator
    private final String generatorClass;
    private final String generatorMethod;
    private final int generatorLine;

    // Original Schema Source
    private final SourceLocation sourceOrigin;
    private final String declarationSnippet;
    private final String modelContext;

    public CompilerDiagnostic(String rawMessage, String generatedFile, int generatedLine, int generatedColumn,
                              String javacError, ErrorCategory category,
                              String generatorClass, String generatorMethod, int generatorLine,
                              SourceLocation sourceOrigin, String declarationSnippet, String modelContext) {
        this.rawMessage = rawMessage;
        this.generatedFile = generatedFile != null ? generatedFile.replace('\\', '/') : "";
        this.generatedLine = generatedLine;
        this.generatedColumn = generatedColumn;
        this.javacError = javacError != null ? javacError.trim() : "";
        this.category = category != null ? category : ErrorCategory.SPECIFICATION_DEFECT;
        this.generatorClass = generatorClass;
        this.generatorMethod = generatorMethod;
        this.generatorLine = generatorLine;
        this.sourceOrigin = sourceOrigin;
        this.declarationSnippet = declarationSnippet;
        this.modelContext = modelContext;
    }

    public static CompilerDiagnostic fromMapping(String rawMessage, String generatedFile, int line, int col,
                                                 String javacError, GenerationMapping mapping) {
        ErrorCategory category = DiagnosticClassifier.classify(javacError, mapping);
        if (mapping != null) {
            return new CompilerDiagnostic(
                    rawMessage, generatedFile, line, col, javacError, category,
                    mapping.getGeneratorClass(), mapping.getGeneratorMethod(), mapping.getGeneratorLine(),
                    mapping.getSourceOrigin(), mapping.getSnippet(), mapping.getGeneratedElement()
            );
        } else {
            return new CompilerDiagnostic(
                    rawMessage, generatedFile, line, col, javacError, category,
                    null, null, -1, null, null, null
            );
        }
    }

    public String getRawMessage() { return rawMessage; }
    public String getGeneratedFile() { return generatedFile; }
    public int getGeneratedLine() { return generatedLine; }
    public int getGeneratedColumn() { return generatedColumn; }
    public String getJavacError() { return javacError; }
    public ErrorCategory getCategory() { return category; }
    public String getGeneratorClass() { return generatorClass; }
    public String getGeneratorMethod() { return generatorMethod; }
    public int getGeneratorLine() { return generatorLine; }
    public SourceLocation getSourceOrigin() { return sourceOrigin; }
    public String getDeclarationSnippet() { return declarationSnippet; }
    public String getModelContext() { return modelContext; }

    /**
     * Formats the diagnostic in the exact human-readable CLI trace defined in spec 2.1.
     */
    public String toCliReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ZENITH COMPILER ERROR] Generated Java compilation failed during build\n\n");
        sb.append("┌─ Generated Target: ").append(generatedFile).append(":").append(generatedLine).append(":").append(generatedColumn).append("\n");
        sb.append("│\n");
        sb.append("│  javac error: ").append(javacError).append("\n");
        sb.append("│\n");
        sb.append("├─ Generator Origin (Zenith Compiler Internals):\n");
        sb.append("│  Generator: ").append(generatorClass != null ? generatorClass : "UnknownGenerator").append("\n");
        sb.append("│  Method:    ").append(generatorMethod != null ? generatorMethod : "unknownMethod")
                .append(generatorLine > 0 ? " (line " + generatorLine + ")" : "").append("\n");
        sb.append("│  Category:  ").append(category).append("\n");
        sb.append("│\n");
        sb.append("└─ Original Schema Source:\n");
        if (sourceOrigin != null) {
            sb.append("   File:        ").append(sourceOrigin.getSourceFile()).append("\n");
            sb.append("   Line:        ").append(sourceOrigin.getStartLine()).append(":").append(sourceOrigin.getStartColumn()).append("\n");
        } else {
            sb.append("   File:        schema.zen (inferred)\n");
        }
        if (declarationSnippet != null && !declarationSnippet.isBlank()) {
            sb.append("   Declaration: ").append(declarationSnippet).append("\n");
        }
        if (modelContext != null && !modelContext.isBlank()) {
            sb.append("   Context:     ").append(modelContext).append("\n");
        }

        // Actionable Plain-English Explanation
        sb.append("│\n");
        sb.append("💡 Explanation & Action:\n");
        if (javacError != null && javacError.contains("cannot find symbol") && declarationSnippet != null) {
            sb.append("   The expression '").append(declarationSnippet).append("' references an unknown symbol or misspelled identifier.\n");
            sb.append("   Check the spelling of your enum constants or field names in your action statement.");
        } else if (category == ErrorCategory.SPECIFICATION_DEFECT) {
            sb.append("   The schema declaration at line ").append(sourceOrigin != null ? sourceOrigin.getStartLine() : "?")
              .append(" generated Java code with incompatible types or invalid references.\n");
            sb.append("   Review the highlighted .zen line and verify all referenced fields, enums, or relations exist.");
        } else {
            sb.append("   Zenith generator encountered an unexpected internal compilation issue.\n");
            sb.append("   Please report this issue with the generator details above.");
        }
        sb.append("\n");
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"category\": \"").append(category).append("\",\n");
        sb.append("  \"generatedTarget\": {\n");
        sb.append("    \"file\": \"").append(escape(generatedFile)).append("\",\n");
        sb.append("    \"line\": ").append(generatedLine).append(",\n");
        sb.append("    \"column\": ").append(generatedColumn).append(",\n");
        sb.append("    \"javacError\": \"").append(escape(javacError)).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"generatorOrigin\": {\n");
        sb.append("    \"class\": \"").append(escape(generatorClass != null ? generatorClass : "")).append("\",\n");
        sb.append("    \"method\": \"").append(escape(generatorMethod != null ? generatorMethod : "")).append("\",\n");
        sb.append("    \"line\": ").append(generatorLine).append("\n");
        sb.append("  }");
        if (sourceOrigin != null) {
            sb.append(",\n  \"schemaOrigin\": {\n");
            sb.append("    \"file\": \"").append(escape(sourceOrigin.getSourceFile())).append("\",\n");
            sb.append("    \"line\": ").append(sourceOrigin.getStartLine()).append(",\n");
            sb.append("    \"column\": ").append(sourceOrigin.getStartColumn()).append(",\n");
            sb.append("    \"snippet\": \"").append(escape(declarationSnippet != null ? declarationSnippet : "")).append("\",\n");
            sb.append("    \"context\": \"").append(escape(modelContext != null ? modelContext : "")).append("\"\n");
            sb.append("  }\n");
        } else {
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

