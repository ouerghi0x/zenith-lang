package dev.zenith.engine.value;

public interface ZenithValue {
    Object getRawValue();
    String asString();
    boolean isTruthy();
    default String getTypeName() {
        return getClass().getSimpleName().replace("Zenith", "").replace("Value", "");
    }
}

