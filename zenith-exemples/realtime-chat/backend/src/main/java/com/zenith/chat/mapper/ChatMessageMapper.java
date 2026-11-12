package com.zenith.chat.mapper;

import com.zenith.chat.entity.ChatMessage;
import com.zenith.chat.dto.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageMapper {

    public ChatMessage toEntity(CreateChatMessageRequest request) {
        if (request == null) return null;
        ChatMessage entity = new ChatMessage();
        entity.setContent(request.content());
        entity.setType(request.type());
        entity.setRecipientId(request.recipientId());
        return entity;
    }

    public void updateEntity(ChatMessage entity, UpdateChatMessageRequest request) {
        if (entity == null || request == null) return;
        if (request.content() != null) {
            entity.setContent(request.content());
        }
        if (request.type() != null) {
            entity.setType(request.type());
        }
        if (request.recipientId() != null) {
            entity.setRecipientId(request.recipientId());
        }
    }

    public ChatMessageResponse toResponse(ChatMessage entity) {
        if (entity == null) return null;
        return new ChatMessageResponse(
            entity.getId(),
            entity.getContent(),
            entity.getType(),
            entity.getRecipientId(),
            entity.getCreatedAt(),
            entity.getSender() != null ? entity.getSender().getId() : null,
            entity.getRoom() != null ? entity.getRoom().getId() : null
        );
    }
}
