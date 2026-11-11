package dev.zenith.engine.value;

import java.util.Objects;

public class ZenithStringValue implements ZenithValue {
    private final String value;

    public ZenithStringValue(String value) {
        this.value = value != null ? value : "";
    }

    @Override
    public Object getRawValue() {
        return value;
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public boolean isTruthy() {
        return !value.isEmpty();
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o instanceof ZenithStringValue) {
            return Objects.equals(value, ((ZenithStringValue) o).value);
        }
        if (o instanceof String) {
            return Objects.equals(value, o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

