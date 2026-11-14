package dev.zenith.generator;

import dev.zenith.ast.*;
import java.util.*;

/** Generates wire contract types and the language-neutral AsyncAPI document. */
public final class StreamGenerator {
    private StreamGenerator() {}

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> result = new ArrayList<>();
        String pkg = file.getConfig().getPackage() + ".event";
        for (EventDef event : file.getEvents()) {
            StringBuilder java = new StringBuilder("package ").append(pkg).append(";\n\n")
                    .append("import java.time.Instant;\nimport java.util.UUID;\n\n")
                    .append("/** Immutable versioned wire contract. */\npublic record ").append(event.getName()).append("(\n")
                    .append("        UUID eventId, String eventType, Instant occurredAt");
            for (FieldDef field : event.getFields()) if (file.findEnum(field.getType().getName()) != null)
                java.insert(java.indexOf("/** Immutable"), "import " + file.getConfig().getPackage() + ".model." + field.getType().getName() + ";\n");
            for (FieldDef field : event.getFields()) java.append(",\n        ").append(javaType(field.getType(), file)).append(' ').append(field.getName());
            java.append("\n) {\n    public ").append(event.getName()).append(" {\n")
                    .append("        if (eventId == null) eventId = UUID.randomUUID();\n")
                    .append("        if (eventType == null) eventType = \"").append(event.getName()).append(".v1\";\n")
                    .append("        if (occurredAt == null) occurredAt = Instant.now();\n    }\n}\n");
            result.add(new JavaSourceFile("src/main/java/" + pkg.replace('.', '/') + "/" + event.getName() + ".java", java.toString()));
        }
        for (StreamDef stream : file.getStreams()) {
            String className = stream.getName() + "Publisher";
            String event = stream.getPayload();
            String binding = Character.toLowerCase(stream.getName().charAt(0)) + stream.getName().substring(1) + "-out-0";
            String java = "package " + pkg + ";\n\n"
                    + "import org.springframework.cloud.stream.function.StreamBridge;\n"
                    + "import org.springframework.stereotype.Component;\n"
                    + "import java.util.Objects;\n\n"
                    + "/** Typed best-effort broker adapter. Transactional outbox relay wiring is a separate feature. */\n"
                    + "@Component\npublic final class " + className + " {\n"
                    + "    private final StreamBridge streamBridge;\n"
                    + "    public " + className + "(StreamBridge streamBridge) { this.streamBridge = streamBridge; }\n"
                    + "    public boolean publish(" + event + " event) {\n"
                    + "        return streamBridge.send(\"" + binding + "\", Objects.requireNonNull(event));\n"
                    + "    }\n}\n";
            result.add(new JavaSourceFile("src/main/java/" + pkg.replace('.', '/') + "/" + className + ".java", java));
            String listener = file.getConfig().isOutboxEnabled()
                    ? "org.springframework.modulith.events.ApplicationModuleListener"
                    : "org.springframework.transaction.event.TransactionalEventListener";
            String annotation = file.getConfig().isOutboxEnabled() ? "@ApplicationModuleListener"
                    : "@TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)";
            String relay = "package " + pkg + ";\n\nimport org.springframework.stereotype.Component;\nimport " + listener + ";\n\n"
                    + "@Component\npublic final class " + stream.getName() + "Relay {\n"
                    + "    private final " + className + " publisher;\n"
                    + "    public " + stream.getName() + "Relay(" + className + " publisher) { this.publisher = publisher; }\n"
                    + "    " + annotation + "\n"
                    + "    public void relay(" + event + " event) {\n"
                    + "        if (!publisher.publish(event)) throw new IllegalStateException(\"Broker rejected stream\");\n"
                    + "    }\n}\n";
            result.add(new JavaSourceFile("src/main/java/" + pkg.replace('.', '/') + "/" + stream.getName() + "Relay.java", relay));
        }
        for (StreamConsumerDef consumer : file.getConsumers()) {
            StreamDef stream = file.getStreams().stream().filter(s -> s.getName().equalsIgnoreCase(consumer.getStream())).findFirst().orElseThrow();
            String event = stream.getPayload();
            String className = consumer.getName() + "Consumer";
            StringBuilder handler = new StringBuilder("package ").append(pkg).append(";\n\n")
                    .append("import java.util.function.Consumer;\nimport org.springframework.context.annotation.Bean;\nimport org.springframework.context.annotation.Configuration;\n")
                    .append("import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\n\n")
                    .append("/** Typed broker consumer. Delivery may be retried and repeated; keep handling idempotent. */\n")
                    .append("@Configuration\npublic class ").append(className).append(" {\n")
                    .append("    private static final Logger log = LoggerFactory.getLogger(").append(className).append(".class);\n")
                    .append("    @Bean\n    public Consumer<").append(event).append("> ").append(functionName(consumer)).append("() {\n")
                    .append("        return payload -> {\n");
            for (String action : consumer.getActions()) {
                String field = action.substring("log payload.".length());
                handler.append("            log.info(\"").append(escapeJava(consumer.getName() + " received " + field + "={}"))
                        .append("\", payload.").append(field).append("());\n");
            }
            handler.append("        };\n    }\n}\n");
            result.add(new JavaSourceFile("src/main/java/" + pkg.replace('.', '/') + "/" + className + ".java", handler.toString()));
        }
        result.add(new JavaSourceFile("docs/asyncapi.yaml", asyncApi(file)));
        return result;
    }

    private static String functionName(StreamConsumerDef consumer) { return "consume" + consumer.getName(); }
    private static String escapeJava(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static String javaType(TypeRef type, ZenithFile file) {
        String name = type.getName();
        if (file.findEnum(name) != null) return name;
        switch (name.toLowerCase()) {
            case "string": return "String";
            case "uuid": return "UUID";
            case "datetime": return "java.time.LocalDateTime";
            case "instant": return "Instant";
            case "date": return "java.time.LocalDate";
            case "boolean": return "Boolean";
            case "int": case "integer": return "Integer";
            case "long": return "Long";
            case "float": return "Float";
            case "double": case "decimal": return "Double";
            default: return name;
        }
    }

    private static String asyncApi(ZenithFile file) {
        StringBuilder out = new StringBuilder("asyncapi: 2.6.0\ninfo:\n  title: ").append(file.getConfig().getApp())
                .append(" Events\n  version: 1.0.0\nchannels:\n");
        Map<String, StringBuilder> publishes = new LinkedHashMap<>();
        Map<String, List<StreamConsumerDef>> subscriptions = new LinkedHashMap<>();
        for (StreamDef stream : file.getStreams()) {
            publishes.computeIfAbsent(stream.getTopic(), key -> new StringBuilder()).append("    publish:\n      operationId: publish")
                    .append(stream.getName()).append("\n      message:\n        name: ").append(stream.getPayload())
                    .append("\n        payload:\n          $ref: '#/components/schemas/").append(stream.getPayload()).append("'\n");
        }
        for (StreamConsumerDef consumer : file.getConsumers()) {
            StreamDef stream = file.getStreams().stream().filter(s -> s.getName().equalsIgnoreCase(consumer.getStream())).findFirst().orElseThrow();
            subscriptions.computeIfAbsent(stream.getTopic(), key -> new ArrayList<>()).add(consumer);
        }
        Set<String> topics = new LinkedHashSet<>();
        topics.addAll(publishes.keySet());
        topics.addAll(subscriptions.keySet());
        for (String topic : topics) {
            out.append("  '").append(topic).append("':\n");
            if (publishes.containsKey(topic)) out.append(publishes.get(topic));
            List<StreamConsumerDef> consumers = subscriptions.get(topic);
            if (consumers != null && !consumers.isEmpty()) {
                StreamConsumerDef consumer = consumers.get(0);
                StreamDef stream = file.getStreams().stream().filter(s -> s.getName().equalsIgnoreCase(consumer.getStream())).findFirst().orElseThrow();
                out.append("    subscribe:\n      operationId: consume").append(consumer.getName())
                        .append("\n      x-zenith-group: '").append(consumer.getGroup()).append("'\n")
                        .append("      message:\n        name: ").append(stream.getPayload()).append("\n        payload:\n          $ref: '#/components/schemas/").append(stream.getPayload()).append("'\n");
                if (consumers.size() > 1) {
                    out.append("    x-zenith-consumers:\n");
                    for (StreamConsumerDef item : consumers) out.append("      - name: '").append(item.getName()).append("'\n        group: '").append(item.getGroup()).append("'\n");
                }
            }
        }
        out.append("components:\n  schemas:\n");
        for (EventDef event : file.getEvents()) {
            out.append("    ").append(event.getName()).append(":\n      type: object\n      required: [eventId, eventType, occurredAt");
            for (FieldDef f : event.getFields()) if (!f.isOptional()) out.append(", ").append(f.getName());
            out.append("]\n      properties:\n        eventId: { type: string, format: uuid }\n        eventType: { type: string, example: '").append(event.getName()).append(".v1' }\n        occurredAt: { type: string, format: date-time }\n");
            for (FieldDef f : event.getFields()) out.append("        ").append(f.getName()).append(": { type: ").append(schemaType(f.getType().getName())).append(" }\n");
        }
        out.append("# Contracts are documentation only; no runtime schema registry is configured.\n");
        return out.toString();
    }

    private static String schemaType(String name) {
        switch (name.toLowerCase()) {
            case "uuid": case "string": case "datetime": case "date": return "string";
            case "boolean": return "boolean";
            case "int": case "integer": case "long": return "integer";
            default: return "number";
        }
    }
}
