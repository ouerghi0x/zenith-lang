package dev.zenith.engine.value;

import java.util.Objects;

public class ZenithNullValue implements ZenithValue {
    public static final ZenithNullValue INSTANCE = new ZenithNullValue();

    private ZenithNullValue() {}

    @Override
    public Object getRawValue() {
        return null;
    }

    @Override
    public String asString() {
        return "null";
    }

    @Override
    public boolean isTruthy() {
        return false;
    }

    @Override
    public String toString() {
        return "null";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ZenithNullValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(0);
    }
}

