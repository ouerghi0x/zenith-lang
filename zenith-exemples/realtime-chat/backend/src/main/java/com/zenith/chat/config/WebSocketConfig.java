package com.zenith.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-Time WebSocket & STOMP Message Broker Configuration.
 * Generated automatically by Zenith DSL (Spec #18 & Peer-to-Peer Spec).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(WebSocketAuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory STOMP broker for broadcast topics (/topic) and peer-to-peer user queues (/queue)
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefix for inbound client-to-server endpoints (@MessageMapping)
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix for user-targeted queues: /user/{username}/queue/...
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ChatSocket STOMP endpoint with SockJS fallback and raw WebSocket support
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authenticate client and resolve Principal on STOMP CONNECT frame
        registration.interceptors(authChannelInterceptor);
    }
}
