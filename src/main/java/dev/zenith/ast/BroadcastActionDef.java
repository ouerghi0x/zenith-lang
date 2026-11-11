package dev.zenith.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a broadcast action statement inside an endpoint or job:
 * broadcast "/topic/chat" {
 *     message = entity.content,
 *     sender = entity.sender.username,
 *     timestamp = now()
 * }
 * or:
 * broadcast "/topic/orders" payload = OrderEventDto
 */
public class BroadcastActionDef {
    private final String topic;
    private final Map<String, String> payloadBindings;
    private final String payloadExpression; // e.g., if broadcast topic entity or DTO directly
    private String sourceFile;
    private int sourceLine;

    public BroadcastActionDef(String topic, Map<String, String> payloadBindings, String payloadExpression) {
        this.topic = topic != null ? topic.trim() : "/topic";
        this.payloadBindings = payloadBindings != null ? new LinkedHashMap<>(payloadBindings) : new LinkedHashMap<>();
        this.payloadExpression = payloadExpression != null ? payloadExpression.trim() : "";
    }

    public String getTopic() {
        return topic;
    }

    public Map<String, String> getPayloadBindings() {
        return Collections.unmodifiableMap(payloadBindings);
    }

    public String getPayloadExpression() {
        return payloadExpression;
    }

    public boolean hasPayloadBindings() {
        return !payloadBindings.isEmpty();
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
    }
}

