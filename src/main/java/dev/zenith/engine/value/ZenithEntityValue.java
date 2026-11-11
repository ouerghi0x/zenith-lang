package dev.zenith.engine.value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ZenithEntityValue implements ZenithValue {
    private final String modelName;
    private final Map<String, ZenithValue> properties;
    private final Map<String, ZenithValue> initialProperties;
    private final Map<String, ZenithValue> mutatedProperties;

    public ZenithEntityValue(String modelName) {
        this(modelName, Collections.emptyMap());
    }

    public ZenithEntityValue(String modelName, Map<String, ZenithValue> initialProps) {
        this.modelName = modelName != null ? modelName : "Entity";
        this.properties = new LinkedHashMap<>();
        this.initialProperties = new LinkedHashMap<>();
        this.mutatedProperties = new LinkedHashMap<>();

        if (initialProps != null) {
            for (Map.Entry<String, ZenithValue> entry : initialProps.entrySet()) {
                ZenithValue val = entry.getValue() != null ? entry.getValue() : ZenithNullValue.INSTANCE;
                this.properties.put(entry.getKey(), val);
                this.initialProperties.put(entry.getKey(), val);
            }
        }
    }

    public String getModelName() {
        return modelName;
    }

    public ZenithValue getProperty(String name) {
        return properties.getOrDefault(name, ZenithNullValue.INSTANCE);
    }

    public void setProperty(String name, ZenithValue value) {
        ZenithValue newVal = value != null ? value : ZenithNullValue.INSTANCE;
        this.properties.put(name, newVal);
        this.mutatedProperties.put(name, newVal);
    }

    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    public Map<String, ZenithValue> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public Map<String, ZenithValue> getInitialProperties() {
        return Collections.unmodifiableMap(initialProperties);
    }

    public Map<String, ZenithValue> getMutatedProperties() {
        return Collections.unmodifiableMap(mutatedProperties);
    }

    @Override
    public Object getRawValue() {
        Map<String, Object> raw = new LinkedHashMap<>();
        for (Map.Entry<String, ZenithValue> entry : properties.entrySet()) {
            raw.put(entry.getKey(), entry.getValue() != null ? entry.getValue().getRawValue() : null);
        }
        return raw;
    }

    @Override
    public String asString() {
        return modelName + " " + properties.toString();
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public String toString() {
        return asString();
    }
}

