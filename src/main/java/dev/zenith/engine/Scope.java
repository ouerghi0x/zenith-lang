package dev.zenith.engine;

import dev.zenith.engine.value.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class Scope {
    private final Scope parent;
    private final Map<String, ZenithValue> bindings;

    public Scope() {
        this(null);
    }

    public Scope(Scope parent) {
        this.parent = parent;
        this.bindings = new LinkedHashMap<>();
    }

    public Scope getParent() {
        return parent;
    }

    public void define(String name, ZenithValue value) {
        bindings.put(name, value != null ? value : ZenithNullValue.INSTANCE);
    }

    public boolean assign(String name, ZenithValue value) {
        if (bindings.containsKey(name)) {
            bindings.put(name, value != null ? value : ZenithNullValue.INSTANCE);
            return true;
        }
        if (parent != null) {
            return parent.assign(name, value);
        }
        return false;
    }

    public ZenithValue resolve(String name) {
        if (bindings.containsKey(name)) {
            return bindings.get(name);
        }
        if (parent != null) {
            return parent.resolve(name);
        }
        return ZenithNullValue.INSTANCE;
    }

    public boolean contains(String name) {
        if (bindings.containsKey(name)) {
            return true;
        }
        if (parent != null) {
            return parent.contains(name);
        }
        return false;
    }

    public Map<String, ZenithValue> getLocalBindings() {
        return Collections.unmodifiableMap(bindings);
    }

    public Map<String, Object> getAllRawBindings() {
        Map<String, Object> all = new LinkedHashMap<>();
        if (parent != null) {
            all.putAll(parent.getAllRawBindings());
        }
        for (Map.Entry<String, ZenithValue> e : bindings.entrySet()) {
            all.put(e.getKey(), e.getValue() != null ? e.getValue().getRawValue() : null);
        }
        return all;
    }
}

