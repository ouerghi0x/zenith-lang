package dev.zenith.ast;

import java.util.Collections;
import java.util.Map;

public class AnnotationDef {
    private final String name;
    private final Map<String, Object> arguments;
    private int sourceLine;

    public AnnotationDef(String name, Map<String, Object> arguments) {
        this(name, arguments, 0);
    }

    public AnnotationDef(String name, Map<String, Object> arguments, int sourceLine) {
        this.name = name;
        this.arguments = arguments != null ? arguments : Collections.emptyMap();
        this.sourceLine = sourceLine;
    }

    public AnnotationDef(String name) {
        this(name, Collections.emptyMap(), 0);
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
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
