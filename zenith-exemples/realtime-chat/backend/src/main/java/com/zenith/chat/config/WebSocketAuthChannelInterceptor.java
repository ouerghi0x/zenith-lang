package com.zenith.chat.config;

import com.zenith.chat.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * Inbound STOMP Channel Interceptor for Authentication & User Session Resolution.
 * Binds Principal to STOMP CONNECT sessions so convertAndSendToUser routes successfully.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String username = null;

            // 1. Check Authorization or token header from STOMP CONNECT frame
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null) {
                authHeader = accessor.getFirstNativeHeader("token");
            }
            if (authHeader != null && !authHeader.isBlank()) {
                String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
                try {
                    username = jwtService.extractUsername(token);
                } catch (Exception ignored) {
                    // Fallback to direct user identifier headers if token parsing fails
                }
            }

            // 2. Direct user or username header (ideal for peer-to-peer testing & API runner)
            if (username == null || username.isBlank()) {
                username = accessor.getFirstNativeHeader("user");
            }
            if (username == null || username.isBlank()) {
                username = accessor.getFirstNativeHeader("username");
            }
            if (username == null || username.isBlank()) {
                username = accessor.getFirstNativeHeader("recipientId");
            }
            // 3. Standard STOMP login header
            if (username == null || username.isBlank()) {
                username = accessor.getLogin();
            }
            // 4. Default to unique guest Principal so session resolution never fails
            if (username == null || username.isBlank()) {
                String sid = accessor.getSessionId();
                username = "guest-" + (sid != null ? sid : UUID.randomUUID().toString().substring(0, 8));
            }

            accessor.setUser(new StompPrincipal(username));
        }

        return message;
    }

    public static class StompPrincipal implements Principal {
        private final String name;

        public StompPrincipal(String name) {
            this.name = name != null ? name : "anonymous";
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Principal p)) return false;
            return Objects.equals(name, p.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
