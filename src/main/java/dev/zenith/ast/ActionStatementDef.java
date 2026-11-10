package dev.zenith.ast;

public class ActionStatementDef {
    private final String statement;
    private final SourceLocation sourceLocation;

    public ActionStatementDef(String statement, SourceLocation sourceLocation) {
        this.statement = statement;
        this.sourceLocation = sourceLocation;
    }

    public String getStatement() {
        return statement;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public String toString() {
        return statement;
    }
}

