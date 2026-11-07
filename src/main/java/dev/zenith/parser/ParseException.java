package dev.zenith.parser;

public class ParseException extends RuntimeException {
    private final int line;
    private final int column;
    private final String rawMessage;
    private String sourceFile;

    public ParseException(String message, int line, int column) {
        this(message, line, column, null);
    }

    public ParseException(String message, int line, int column, String sourceFile) {
        super(formatMessage(message, line, column, sourceFile));
        this.rawMessage = message;
        this.line = line;
        this.column = column;
        this.sourceFile = sourceFile;
    }

    private static String formatMessage(String message, int line, int column, String sourceFile) {
        if (sourceFile != null && !sourceFile.isEmpty()) {
            return String.format("[%s:%d:%d] Syntax error: %s", sourceFile, line, column, message);
        }
        return String.format("Syntax error at line %d, col %d: %s", line, column, message);
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }
}
