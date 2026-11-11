package dev.zenith.engine.value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ZenithListValue implements ZenithValue {
    private final List<ZenithValue> elements;

    public ZenithListValue() {
        this.elements = new ArrayList<>();
    }

    public ZenithListValue(List<ZenithValue> elements) {
        this.elements = elements != null ? new ArrayList<>(elements) : new ArrayList<>();
    }

    public List<ZenithValue> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public void add(ZenithValue value) {
        this.elements.add(value != null ? value : ZenithNullValue.INSTANCE);
    }

    @Override
    public Object getRawValue() {
        return elements.stream().map(ZenithValue::getRawValue).collect(Collectors.toList());
    }

    @Override
    public String asString() {
        return "[" + elements.stream().map(ZenithValue::asString).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public boolean isTruthy() {
        return !elements.isEmpty();
    }

    @Override
    public String toString() {
        return asString();
    }
}

