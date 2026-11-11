package dev.zenith.engine.value;

import java.util.Objects;

public class ZenithBooleanValue implements ZenithValue {
    public static final ZenithBooleanValue TRUE = new ZenithBooleanValue(true);
    public static final ZenithBooleanValue FALSE = new ZenithBooleanValue(false);

    private final boolean value;

    public static ZenithBooleanValue of(boolean b) {
        return b ? TRUE : FALSE;
    }

    private ZenithBooleanValue(boolean value) {
        this.value = value;
    }

    public boolean getBoolean() {
        return value;
    }

    @Override
    public Object getRawValue() {
        return value;
    }

    @Override
    public String asString() {
        return Boolean.toString(value);
    }

    @Override
    public boolean isTruthy() {
        return value;
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o instanceof ZenithBooleanValue) {
            return value == ((ZenithBooleanValue) o).value;
        }
        if (o instanceof Boolean) {
            return value == (Boolean) o;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

