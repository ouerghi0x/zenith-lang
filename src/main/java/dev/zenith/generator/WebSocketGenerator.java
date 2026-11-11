package dev.zenith.generator;

import dev.zenith.ast.SocketDef;
import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

public class WebSocketGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> sources = new ArrayList<>();
        if (!file.hasSockets()) {
            return sources;
        }

        String basePkg = file.getConfig().getPackage();
        String configPkg = basePkg + ".config";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(configPkg).append(";\n\n");
        sb.append("import org.springframework.context.annotation.Configuration;\n");
        sb.append("import org.springframework.messaging.simp.config.MessageBrokerRegistry;\n");
        sb.append("import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;\n");
        sb.append("import org.springframework.web.socket.config.annotation.StompEndpointRegistry;\n");
        sb.append("import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;\n\n");

        sb.append("/**\n");
        sb.append(" * Real-Time WebSocket & STOMP Message Broker Configuration.\n");
        sb.append(" * Generated automatically by Zenith DSL (Spec #18).\n");
        sb.append(" */\n");
        sb.append("@Configuration\n");
        sb.append("@EnableWebSocketMessageBroker\n");
        sb.append("public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {\n\n");

        sb.append("    @Override\n");
        sb.append("    public void configureMessageBroker(MessageBrokerRegistry registry) {\n");
        sb.append("        // In-memory STOMP broker for broadcast topics & point-to-point queues\n");
        sb.append("        registry.enableSimpleBroker(\"/topic\", \"/queue\");\n");
        sb.append("        registry.setApplicationDestinationPrefixes(\"/app\");\n");
        sb.append("        registry.setUserDestinationPrefix(\"/user\");\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void registerStompEndpoints(StompEndpointRegistry registry) {\n");

        for (SocketDef s : file.getSockets()) {
            String ep = s.getEndpoint().startsWith("/") ? s.getEndpoint() : "/" + s.getEndpoint();
            sb.append("        // ").append(s.getName()).append(" STOMP endpoint with SockJS fallback\n");
            sb.append("        registry.addEndpoint(\"").append(ep).append("\")\n");
            sb.append("                .setAllowedOriginPatterns(\"*\")\n");
            sb.append("                .withSockJS();\n");
            sb.append("        registry.addEndpoint(\"").append(ep).append("\")\n");
            sb.append("                .setAllowedOriginPatterns(\"*\");\n");
        }

        sb.append("    }\n");
        sb.append("}\n");

        String relativePath = "src/main/java/" + configPkg.replace('.', '/') + "/WebSocketConfig.java";
        sources.add(new JavaSourceFile(relativePath, sb.toString()));
        return sources;
    }
}

