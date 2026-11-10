package dev.zenith.ast;

/**
 * Tracks the precise source file, line, and column coordinates of an AST element
 * to power compiler diagnostics and generated code traceability.
 */
public class SourceLocation {
    private final String sourceFile;
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;

    public SourceLocation(String sourceFile, int startLine, int startColumn) {
        this(sourceFile, startLine, startColumn, startLine, startColumn);
    }

    public SourceLocation(String sourceFile, int startLine, int startColumn, int endLine, int endColumn) {
        this.sourceFile = sourceFile;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    @Override
    public String toString() {
        return (sourceFile != null ? sourceFile : "unknown") + ":" + startLine + ":" + startColumn;
    }
}

