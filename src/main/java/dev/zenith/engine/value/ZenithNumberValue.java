package dev.zenith.engine.value;

import java.math.BigDecimal;
import java.util.Objects;

public class ZenithNumberValue implements ZenithValue {
    private final BigDecimal value;

    public ZenithNumberValue(BigDecimal value) {
        this.value = value != null ? value : BigDecimal.ZERO;
    }

    public ZenithNumberValue(double value) {
        this.value = BigDecimal.valueOf(value);
    }

    public ZenithNumberValue(long value) {
        this.value = BigDecimal.valueOf(value);
    }

    public ZenithNumberValue(String value) {
        this.value = new BigDecimal(value);
    }

    public BigDecimal getDecimalValue() {
        return value;
    }

    @Override
    public Object getRawValue() {
        return value;
    }

    @Override
    public String asString() {
        return value.stripTrailingZeros().toPlainString();
    }

    @Override
    public boolean isTruthy() {
        return value.compareTo(BigDecimal.ZERO) != 0;
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o instanceof ZenithNumberValue) {
            return value.compareTo(((ZenithNumberValue) o).value) == 0;
        }
        if (o instanceof Number) {
            return value.compareTo(new BigDecimal(o.toString())) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.stripTrailingZeros());
    }
}

