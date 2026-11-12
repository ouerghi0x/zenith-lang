package dev.zenith.generator;

import dev.zenith.ast.SocketDef;
import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates Spring Boot STOMP WebSocket broker configuration,
 * inbound channel authentication interceptors, and peer-to-peer controllers.
 * Implements Zenith Specification #18 and Peer-to-Peer STOMP architecture.
 */
public class WebSocketGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> sources = new ArrayList<>();
        if (!file.hasSockets()) {
            return sources;
        }

        String basePkg = file.getConfig().getPackage();
        String configPkg = basePkg + ".config";
        String controllerPkg = basePkg + ".controller";

        List<SocketDef> socketList = new ArrayList<>(file.getSockets());
        if (socketList.isEmpty()) {
            socketList.add(new SocketDef("DefaultSocket", "/ws", "/topic/chat", "simple"));
        }

        // 1. WebSocketAuthChannelInterceptor.java
        sources.add(generateAuthChannelInterceptor(configPkg, basePkg, file));

        // 2. WebSocketConfig.java
        sources.add(generateWebSocketConfig(configPkg, socketList));

        // 3. WebSocketMessageController.java
        sources.add(generateMessageController(controllerPkg, socketList.get(0).getTopic()));

        // 4. WebSocketRealtimeIntegrationTest.java
        sources.add(generateIntegrationTest(basePkg, socketList.get(0).getEndpoint()));

        return sources;
    }

    private static JavaSourceFile generateWebSocketConfig(String configPkg, List<SocketDef> sockets) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(configPkg).append(";\n\n");
        sb.append("import org.springframework.context.annotation.Configuration;\n");
        sb.append("import org.springframework.messaging.simp.config.ChannelRegistration;\n");
        sb.append("import org.springframework.messaging.simp.config.MessageBrokerRegistry;\n");
        sb.append("import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;\n");
        sb.append("import org.springframework.web.socket.config.annotation.StompEndpointRegistry;\n");
        sb.append("import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;\n\n");

        sb.append("/**\n");
        sb.append(" * Real-Time WebSocket & STOMP Message Broker Configuration.\n");
        sb.append(" * Generated automatically by Zenith DSL (Spec #18 & Peer-to-Peer Spec).\n");
        sb.append(" */\n");
        sb.append("@Configuration\n");
        sb.append("@EnableWebSocketMessageBroker\n");
        sb.append("public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {\n\n");

        sb.append("    private final WebSocketAuthChannelInterceptor authChannelInterceptor;\n\n");
        sb.append("    public WebSocketConfig(WebSocketAuthChannelInterceptor authChannelInterceptor) {\n");
        sb.append("        this.authChannelInterceptor = authChannelInterceptor;\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void configureMessageBroker(MessageBrokerRegistry registry) {\n");
        sb.append("        // In-memory STOMP broker for broadcast topics (/topic) and peer-to-peer user queues (/queue)\n");
        sb.append("        registry.enableSimpleBroker(\"/topic\", \"/queue\");\n");
        sb.append("        // Prefix for inbound client-to-server endpoints (@MessageMapping)\n");
        sb.append("        registry.setApplicationDestinationPrefixes(\"/app\");\n");
        sb.append("        // Prefix for user-targeted queues: /user/{username}/queue/...\n");
        sb.append("        registry.setUserDestinationPrefix(\"/user\");\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void registerStompEndpoints(StompEndpointRegistry registry) {\n");

        for (SocketDef s : sockets) {
            String ep = s.getEndpoint().startsWith("/") ? s.getEndpoint() : "/" + s.getEndpoint();
            sb.append("        // ").append(s.getName()).append(" STOMP endpoint with SockJS fallback and raw WebSocket support\n");
            sb.append("        registry.addEndpoint(\"").append(ep).append("\")\n");
            sb.append("                .setAllowedOriginPatterns(\"*\")\n");
            sb.append("                .withSockJS();\n");
            sb.append("        registry.addEndpoint(\"").append(ep).append("\")\n");
            sb.append("                .setAllowedOriginPatterns(\"*\");\n");
        }

        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void configureClientInboundChannel(ChannelRegistration registration) {\n");
        sb.append("        // Authenticate client and resolve Principal on STOMP CONNECT frame\n");
        sb.append("        registration.interceptors(authChannelInterceptor);\n");
        sb.append("    }\n");
        sb.append("}\n");

        String relativePath = "src/main/java/" + configPkg.replace('.', '/') + "/WebSocketConfig.java";
        return new JavaSourceFile(relativePath, sb.toString());
    }

    private static JavaSourceFile generateAuthChannelInterceptor(String configPkg, String basePkg, ZenithFile file) {
        boolean hasAuth = file.hasAuth();
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(configPkg).append(";\n\n");

        if (hasAuth) {
            sb.append("import ").append(basePkg).append(".security.JwtService;\n");
        }
        sb.append("import org.springframework.messaging.Message;\n");
        sb.append("import org.springframework.messaging.MessageChannel;\n");
        sb.append("import org.springframework.messaging.simp.stomp.StompCommand;\n");
        sb.append("import org.springframework.messaging.simp.stomp.StompHeaderAccessor;\n");
        sb.append("import org.springframework.messaging.support.ChannelInterceptor;\n");
        sb.append("import org.springframework.messaging.support.MessageHeaderAccessor;\n");
        sb.append("import org.springframework.stereotype.Component;\n\n");
        sb.append("import java.security.Principal;\n");
        sb.append("import java.util.Objects;\n");
        sb.append("import java.util.UUID;\n\n");

        sb.append("/**\n");
        sb.append(" * Inbound STOMP Channel Interceptor for Authentication & User Session Resolution.\n");
        sb.append(" * Binds Principal to STOMP CONNECT sessions so convertAndSendToUser routes successfully.\n");
        sb.append(" */\n");
        sb.append("@Component\n");
        sb.append("public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {\n\n");

        if (hasAuth) {
            sb.append("    private final JwtService jwtService;\n\n");
            sb.append("    public WebSocketAuthChannelInterceptor(JwtService jwtService) {\n");
            sb.append("        this.jwtService = jwtService;\n");
            sb.append("    }\n\n");
        } else {
            sb.append("    public WebSocketAuthChannelInterceptor() {}\n\n");
        }

        sb.append("    @Override\n");
        sb.append("    public Message<?> preSend(Message<?> message, MessageChannel channel) {\n");
        sb.append("        StompHeaderAccessor accessor =\n");
        sb.append("                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);\n\n");
        sb.append("        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {\n");
        sb.append("            String username = null;\n\n");

        if (hasAuth) {
            sb.append("            // 1. Check Authorization or token header from STOMP CONNECT frame\n");
            sb.append("            String authHeader = accessor.getFirstNativeHeader(\"Authorization\");\n");
            sb.append("            if (authHeader == null) {\n");
            sb.append("                authHeader = accessor.getFirstNativeHeader(\"token\");\n");
            sb.append("            }\n");
            sb.append("            if (authHeader != null && !authHeader.isBlank()) {\n");
            sb.append("                String token = authHeader.startsWith(\"Bearer \") ? authHeader.substring(7) : authHeader;\n");
            sb.append("                try {\n");
            sb.append("                    username = jwtService.extractUsername(token);\n");
            sb.append("                } catch (Exception ignored) {\n");
            sb.append("                    // Fallback to direct user identifier headers if token parsing fails\n");
            sb.append("                }\n");
            sb.append("            }\n\n");
        }

        sb.append("            // 2. Direct user or username header (ideal for peer-to-peer testing & API runner)\n");
        sb.append("            if (username == null || username.isBlank()) {\n");
        sb.append("                username = accessor.getFirstNativeHeader(\"user\");\n");
        sb.append("            }\n");
        sb.append("            if (username == null || username.isBlank()) {\n");
        sb.append("                username = accessor.getFirstNativeHeader(\"username\");\n");
        sb.append("            }\n");
        sb.append("            if (username == null || username.isBlank()) {\n");
        sb.append("                username = accessor.getFirstNativeHeader(\"recipientId\");\n");
        sb.append("            }\n");
        sb.append("            // 3. Standard STOMP login header\n");
        sb.append("            if (username == null || username.isBlank()) {\n");
        sb.append("                username = accessor.getLogin();\n");
        sb.append("            }\n");
        sb.append("            // 4. Default to unique guest Principal so session resolution never fails\n");
        sb.append("            if (username == null || username.isBlank()) {\n");
        sb.append("                String sid = accessor.getSessionId();\n");
        sb.append("                username = \"guest-\" + (sid != null ? sid : UUID.randomUUID().toString().substring(0, 8));\n");
        sb.append("            }\n\n");

        sb.append("            accessor.setUser(new StompPrincipal(username));\n");
        sb.append("        }\n\n");
        sb.append("        return message;\n");
        sb.append("    }\n\n");

        // Static inner StompPrincipal
        sb.append("    public static class StompPrincipal implements Principal {\n");
        sb.append("        private final String name;\n\n");
        sb.append("        public StompPrincipal(String name) {\n");
        sb.append("            this.name = name != null ? name : \"anonymous\";\n");
        sb.append("        }\n\n");
        sb.append("        @Override\n");
        sb.append("        public String getName() {\n");
        sb.append("            return name;\n");
        sb.append("        }\n\n");
        sb.append("        @Override\n");
        sb.append("        public boolean equals(Object o) {\n");
        sb.append("            if (this == o) return true;\n");
        sb.append("            if (!(o instanceof Principal p)) return false;\n");
        sb.append("            return Objects.equals(name, p.getName());\n");
        sb.append("        }\n\n");
        sb.append("        @Override\n");
        sb.append("        public int hashCode() {\n");
        sb.append("            return Objects.hash(name);\n");
        sb.append("        }\n\n");
        sb.append("        @Override\n");
        sb.append("        public String toString() {\n");
        sb.append("            return name;\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        String relativePath = "src/main/java/" + configPkg.replace('.', '/') + "/WebSocketAuthChannelInterceptor.java";
        return new JavaSourceFile(relativePath, sb.toString());
    }

    private static JavaSourceFile generateMessageController(String controllerPkg, String defaultTopic) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(controllerPkg).append(";\n\n");
        sb.append("import org.springframework.messaging.handler.annotation.MessageMapping;\n");
        sb.append("import org.springframework.messaging.handler.annotation.Payload;\n");
        sb.append("import org.springframework.messaging.simp.SimpMessagingTemplate;\n");
        sb.append("import org.springframework.stereotype.Controller;\n\n");
        sb.append("import java.security.Principal;\n");
        sb.append("import java.util.Map;\n\n");

        sb.append("/**\n");
        sb.append(" * STOMP Controller for Client-to-Server and Peer-to-Peer Messaging.\n");
        sb.append(" * Routes messages to individual users (/user/queue/messages) or broadcast topics.\n");
        sb.append(" */\n");
        sb.append("@Controller\n");
        sb.append("public class WebSocketMessageController {\n\n");
        sb.append("    private final SimpMessagingTemplate messagingTemplate;\n\n");
        sb.append("    public WebSocketMessageController(SimpMessagingTemplate messagingTemplate) {\n");
        sb.append("        this.messagingTemplate = messagingTemplate;\n");
        sb.append("    }\n\n");

        sb.append("    @MessageMapping(\"/chat.send\")\n");
        sb.append("    public void sendDirectMessage(@Payload Map<String, Object> payload, Principal sender) {\n");
        sb.append("        String recipientId = (String) payload.get(\"recipientId\");\n");
        sb.append("        if (recipientId == null) {\n");
        sb.append("            recipientId = (String) payload.get(\"recipient\");\n");
        sb.append("        }\n");
        sb.append("        if (sender != null && !payload.containsKey(\"senderId\")) {\n");
        sb.append("            payload.put(\"senderId\", sender.getName());\n");
        sb.append("        }\n");
        sb.append("        if (recipientId != null && !recipientId.isBlank()) {\n");
        sb.append("            messagingTemplate.convertAndSendToUser(recipientId, \"/queue/messages\", payload);\n");
        sb.append("        } else {\n");
        sb.append("            messagingTemplate.convertAndSend(\"").append(defaultTopic != null ? defaultTopic : "/topic/chat").append("\", payload);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    @MessageMapping(\"/chat.broadcast\")\n");
        sb.append("    public void broadcastMessage(@Payload Map<String, Object> payload, Principal sender) {\n");
        sb.append("        if (sender != null && !payload.containsKey(\"senderId\")) {\n");
        sb.append("            payload.put(\"senderId\", sender.getName());\n");
        sb.append("        }\n");
        sb.append("        messagingTemplate.convertAndSend(\"").append(defaultTopic != null ? defaultTopic : "/topic/chat").append("\", payload);\n");
        sb.append("    }\n");
        sb.append("}\n");

        String relativePath = "src/main/java/" + controllerPkg.replace('.', '/') + "/WebSocketMessageController.java";
        return new JavaSourceFile(relativePath, sb.toString());
    }

    private static JavaSourceFile generateIntegrationTest(String basePkg, String endpoint) {
        String ep = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".websocket;\n\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
        sb.append("import org.springframework.boot.test.context.SpringBootTest;\n");
        sb.append("import org.springframework.boot.test.web.server.LocalServerPort;\n");
        sb.append("import org.springframework.messaging.converter.MappingJackson2MessageConverter;\n");
        sb.append("import org.springframework.messaging.simp.SimpMessagingTemplate;\n");
        sb.append("import org.springframework.messaging.simp.stomp.StompFrameHandler;\n");
        sb.append("import org.springframework.messaging.simp.stomp.StompHeaders;\n");
        sb.append("import org.springframework.messaging.simp.stomp.StompSession;\n");
        sb.append("import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;\n");
        sb.append("import org.springframework.web.socket.WebSocketHttpHeaders;\n");
        sb.append("import org.springframework.web.socket.client.standard.StandardWebSocketClient;\n");
        sb.append("import org.springframework.web.socket.messaging.WebSocketStompClient;\n\n");
        sb.append("import java.lang.reflect.Type;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.concurrent.CompletableFuture;\n");
        sb.append("import java.util.concurrent.TimeUnit;\n\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");

        sb.append("@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)\n");
        sb.append("public class WebSocketRealtimeIntegrationTest {\n\n");
        sb.append("    @LocalServerPort\n");
        sb.append("    private int port;\n\n");
        sb.append("    @Autowired\n");
        sb.append("    private SimpMessagingTemplate messagingTemplate;\n\n");

        sb.append("    @Test\n");
        sb.append("    void testPeerToPeerDirectMessageDelivery() throws Exception {\n");
        sb.append("        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());\n");
        sb.append("        stompClient.setMessageConverter(new MappingJackson2MessageConverter());\n\n");

        sb.append("        StompHeaders connectHeaders = new StompHeaders();\n");
        sb.append("        connectHeaders.add(\"user\", \"test-recipient\");\n\n");

        sb.append("        CompletableFuture<Map<String, Object>> messageFuture = new CompletableFuture<>();\n\n");

        sb.append("        String wsUrl = \"ws://localhost:\" + port + \"").append(ep).append("/websocket\";\n");
        sb.append("        StompSession session = stompClient.connectAsync(\n");
        sb.append("                wsUrl,\n");
        sb.append("                new WebSocketHttpHeaders(),\n");
        sb.append("                connectHeaders,\n");
        sb.append("                new StompSessionHandlerAdapter() {}\n");
        sb.append("        ).get(8, TimeUnit.SECONDS);\n\n");

        sb.append("        session.subscribe(\"/user/queue/messages\", new StompFrameHandler() {\n");
        sb.append("            @Override\n");
        sb.append("            public Type getPayloadType(StompHeaders headers) {\n");
        sb.append("                return Map.class;\n");
        sb.append("            }\n\n");
        sb.append("            @Override\n");
        sb.append("            public void handleFrame(StompHeaders headers, Object payload) {\n");
        sb.append("                @SuppressWarnings(\"unchecked\")\n");
        sb.append("                Map<String, Object> map = (Map<String, Object>) payload;\n");
        sb.append("                messageFuture.complete(map);\n");
        sb.append("            }\n");
        sb.append("        });\n\n");
        sb.append("        // Give STOMP broker brief moment to complete asynchronous subscription registration\n");
        sb.append("        Thread.sleep(500);\n\n");
        sb.append("        // Server-side dispatch to user targeted queue\n");
        sb.append("        messagingTemplate.convertAndSendToUser(\n");
        sb.append("                \"test-recipient\",\n");
        sb.append("                \"/queue/messages\",\n");
        sb.append("                Map.of(\"content\", \"Hello from Zenith Realtime!\", \"senderId\", \"zenith-admin\")\n");
        sb.append("        );\n\n");

        sb.append("        Map<String, Object> received = messageFuture.get(8, TimeUnit.SECONDS);\n");
        sb.append("        assertNotNull(received, \"Should receive message on /user/queue/messages\");\n");
        sb.append("        assertEquals(\"Hello from Zenith Realtime!\", received.get(\"content\"));\n");
        sb.append("        assertEquals(\"zenith-admin\", received.get(\"senderId\"));\n\n");

        sb.append("        session.disconnect();\n");
        sb.append("    }\n");
        sb.append("}\n");

        String relativePath = "src/test/java/" + basePkg.replace('.', '/') + "/websocket/WebSocketRealtimeIntegrationTest.java";
        return new JavaSourceFile(relativePath, sb.toString());
    }
}
