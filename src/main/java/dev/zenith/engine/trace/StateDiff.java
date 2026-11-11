package dev.zenith.engine.trace;

public class StateDiff {
    private final String fieldName;
    private final Object beforeValue;
    private final Object afterValue;

    public StateDiff(String fieldName, Object beforeValue, Object afterValue) {
        this.fieldName = fieldName;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getBeforeValue() {
        return beforeValue;
    }

    public Object getAfterValue() {
        return afterValue;
    }
}

