package com.zenith.chat.mapper;

import com.zenith.chat.entity.ChatUser;
import com.zenith.chat.dto.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatUserMapper {

    public ChatUser toEntity(CreateChatUserRequest request) {
        if (request == null) return null;
        ChatUser entity = new ChatUser();
        entity.setUsername(request.username());
        entity.setEmail(request.email());
        entity.setPassword(request.password());
        entity.setRole(request.role());
        entity.setAvatarUrl(request.avatarUrl());
        return entity;
    }

    public void updateEntity(ChatUser entity, UpdateChatUserRequest request) {
        if (entity == null || request == null) return;
        if (request.username() != null) {
            entity.setUsername(request.username());
        }
        if (request.email() != null) {
            entity.setEmail(request.email());
        }
        if (request.password() != null) {
            entity.setPassword(request.password());
        }
        if (request.role() != null) {
            entity.setRole(request.role());
        }
        if (request.avatarUrl() != null) {
            entity.setAvatarUrl(request.avatarUrl());
        }
    }

    public ChatUserResponse toResponse(ChatUser entity) {
        if (entity == null) return null;
        return new ChatUserResponse(
            entity.getId(),
            entity.getUsername(),
            entity.getEmail(),
            entity.getRole(),
            entity.getAvatarUrl(),
            entity.getCreatedAt()
        );
    }
}
