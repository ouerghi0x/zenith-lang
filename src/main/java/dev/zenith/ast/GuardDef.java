package dev.zenith.ast;

public class GuardDef {
    private final String expression;
    private final String errorMessage;

    public GuardDef(String expression, String errorMessage) {
        this.expression = expression != null ? expression.trim() : "";
        this.errorMessage = errorMessage != null ? errorMessage.trim() : "Condition not met";
    }

    public String getExpression() {
        return expression;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
