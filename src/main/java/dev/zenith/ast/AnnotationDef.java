package dev.zenith.ast;

import java.util.Collections;
import java.util.Map;

public class AnnotationDef {
    private final String name;
    private final Map<String, Object> arguments;

    public AnnotationDef(String name, Map<String, Object> arguments) {
        this.name = name;
        this.arguments = arguments != null ? arguments : Collections.emptyMap();
    }

    public AnnotationDef(String name) {
        this(name, Collections.emptyMap());
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public Object getArgument(String key) {
        return arguments.get(key);
    }

    public boolean hasArgument(String key) {
        return arguments.containsKey(key);
    }

    @Override
    public String toString() {
        if (arguments.isEmpty()) {
            return "@" + name;
        }
        return "@" + name + arguments;
    }
}
