package dev.zenith.analyzer;

public class AnalysisException extends RuntimeException {
    private final String rawMessage;
    private final String sourceFile;
    private final int line;
    private final int column;

    public AnalysisException(String message) {
        this(message, null, 1, 1);
    }

    public AnalysisException(String message, String sourceFile, int line) {
        this(message, sourceFile, line, 1);
    }

    public AnalysisException(String message, String sourceFile, int line, int column) {
        super(formatMessage(message, sourceFile, line, column));
        this.rawMessage = message;
        this.sourceFile = sourceFile;
        this.line = line;
        this.column = column;
    }

    private static String formatMessage(String message, String sourceFile, int line, int column) {
        if (sourceFile != null && !sourceFile.isEmpty()) {
            if (line > 0) {
                return String.format("[%s:%d] Semantic error: %s", sourceFile, line, message);
            }
            return String.format("[%s] Semantic error: %s", sourceFile, message);
        }
        return "Semantic analysis error: " + message;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
