package com.zenith.chat.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketRealtimeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void testPeerToPeerDirectMessageDelivery() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("user", "test-recipient");

        CompletableFuture<Map<String, Object>> messageFuture = new CompletableFuture<>();

        String wsUrl = "ws://localhost:" + port + "/ws/websocket";
        StompSession session = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(8, TimeUnit.SECONDS);

        session.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) payload;
                messageFuture.complete(map);
            }
        });

        // Give STOMP broker brief moment to complete asynchronous subscription registration
        Thread.sleep(500);

        // Server-side dispatch to user targeted queue
        messagingTemplate.convertAndSendToUser(
                "test-recipient",
                "/queue/messages",
                Map.of("content", "Hello from Zenith Realtime!", "senderId", "zenith-admin")
        );

        Map<String, Object> received = messageFuture.get(8, TimeUnit.SECONDS);
        assertNotNull(received, "Should receive message on /user/queue/messages");
        assertEquals("Hello from Zenith Realtime!", received.get("content"));
        assertEquals("zenith-admin", received.get("senderId"));

        session.disconnect();
    }
}
