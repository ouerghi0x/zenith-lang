package dev.zenith.engine.trace;

public class DispatchedEffect {
    private final String type;
    private final String contract;
    private final java.util.Map<String, Object> arguments;

    public DispatchedEffect(String type, String contract, java.util.Map<String, Object> arguments) {
        this.type = type != null ? type : "EFFECT";
        this.contract = contract != null ? contract : "";
        this.arguments = arguments != null ? new java.util.LinkedHashMap<>(arguments) : new java.util.LinkedHashMap<>();
    }

    public String getType() {
        return type;
    }

    public String getContract() {
        return contract;
    }

    public java.util.Map<String, Object> getArguments() {
        return java.util.Collections.unmodifiableMap(arguments);
    }
}

