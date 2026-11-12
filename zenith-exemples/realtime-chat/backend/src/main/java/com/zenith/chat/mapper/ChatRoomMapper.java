package com.zenith.chat.mapper;

import com.zenith.chat.entity.ChatRoom;
import com.zenith.chat.dto.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatRoomMapper {

    public ChatRoom toEntity(CreateChatRoomRequest request) {
        if (request == null) return null;
        ChatRoom entity = new ChatRoom();
        entity.setName(request.name());
        entity.setDescription(request.description());
        return entity;
    }

    public void updateEntity(ChatRoom entity, UpdateChatRoomRequest request) {
        if (entity == null || request == null) return;
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
    }

    public ChatRoomResponse toResponse(ChatRoom entity) {
        if (entity == null) return null;
        return new ChatRoomResponse(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getCreatedAt()
        );
    }
}
