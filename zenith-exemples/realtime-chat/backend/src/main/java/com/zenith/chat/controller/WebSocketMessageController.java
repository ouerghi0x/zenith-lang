package com.zenith.chat.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP Controller for Client-to-Server and Peer-to-Peer Messaging.
 * Routes messages to individual users (/user/queue/messages) or broadcast topics.
 */
@Controller
public class WebSocketMessageController {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketMessageController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendDirectMessage(@Payload Map<String, Object> payload, Principal sender) {
        String recipientId = (String) payload.get("recipientId");
        if (recipientId == null) {
            recipientId = (String) payload.get("recipient");
        }
        if (sender != null && !payload.containsKey("senderId")) {
            payload.put("senderId", sender.getName());
        }
        if (recipientId != null && !recipientId.isBlank()) {
            messagingTemplate.convertAndSendToUser(recipientId, "/queue/messages", payload);
        } else {
            messagingTemplate.convertAndSend("/topic/chat", payload);
        }
    }

    @MessageMapping("/chat.broadcast")
    public void broadcastMessage(@Payload Map<String, Object> payload, Principal sender) {
        if (sender != null && !payload.containsKey("senderId")) {
            payload.put("senderId", sender.getName());
        }
        messagingTemplate.convertAndSend("/topic/chat", payload);
    }
}
