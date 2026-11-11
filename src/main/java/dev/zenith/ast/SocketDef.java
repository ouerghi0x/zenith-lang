package dev.zenith.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a WebSocket broker and endpoint declaration:
 * socket ChatSocket {
 *     endpoint: "/ws"
 *     topic: "/topic/messages"
 *     broker: "simple" // or "rabbitmq"
 *     payload: MessageDto
 * }
 */
public class SocketDef {
    private final String name;
    private final String endpoint;
    private final String topic;
    private final String broker;
    private final String payload;
    private final Map<String, String> properties;
    private String sourceFile;
    private int sourceLine;

    public SocketDef(String name, String endpoint, String topic, String broker, String payload, Map<String, String> properties) {
        this.name = name;
        this.endpoint = endpoint != null && !endpoint.isEmpty() ? endpoint : "/ws";
        this.topic = topic != null && !topic.isEmpty() ? topic : "/topic/messages";
        this.broker = broker != null && !broker.isEmpty() ? broker : "simple";
        this.payload = payload != null ? payload : "";
        this.properties = properties != null ? new LinkedHashMap<>(properties) : new LinkedHashMap<>();
    }

    public String getName() {
        return name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getTopic() {
        return topic;
    }

    public String getBroker() {
        return broker;
    }

    public String getPayload() {
        return payload;
    }

    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
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

