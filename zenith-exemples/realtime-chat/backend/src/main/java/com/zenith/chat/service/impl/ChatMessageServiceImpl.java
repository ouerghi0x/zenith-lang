package com.zenith.chat.service.impl;

import com.zenith.chat.service.ChatMessageService;
import com.zenith.chat.entity.ChatMessage;
import com.zenith.chat.dto.*;
import com.zenith.chat.mapper.ChatMessageMapper;
import com.zenith.chat.repository.ChatMessageRepository;
import com.zenith.chat.model.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import com.zenith.chat.exception.ResourceNotFoundException;
import com.zenith.chat.repository.ChatUserRepository;
import com.zenith.chat.entity.ChatUser;
import com.zenith.chat.repository.ChatRoomRepository;
import com.zenith.chat.entity.ChatRoom;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageRepository repository;
    private final ChatMessageMapper mapper;
    private final ChatUserRepository chatuserRepository;
    private final ChatRoomRepository chatroomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageServiceImpl(
        ChatMessageRepository repository,
        ChatMessageMapper mapper,
        ChatUserRepository chatuserRepository,
        ChatRoomRepository chatroomRepository,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.chatuserRepository = chatuserRepository;
        this.chatroomRepository = chatroomRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getAll(int page, int size, String sort, String direction, String search, MessageType type) {
        Sort sortObj = "desc".equalsIgnoreCase(direction) ? Sort.by(sort).descending() : Sort.by(sort).ascending();
        Pageable pageable = PageRequest.of(page, size, sortObj);
        Specification<ChatMessage> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ChatMessage> entityPage = repository.findAll(spec, pageable);
        return PageResponse.of(entityPage.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ChatMessageResponse getById(UUID id) {
        ChatMessage entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChatMessage not found with id: " + id));
        return mapper.toResponse(entity);
    }

    @Override
    public ChatMessageResponse create(CreateChatMessageRequest request) {
        ChatMessage entity = mapper.toEntity(request);
        if (request.senderId() != null) {
            ChatUser relEntity = chatuserRepository.findById(request.senderId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatUser not found with id: " + request.senderId()));
            entity.setSender(relEntity);
        }
        if (request.roomId() != null) {
            ChatRoom relEntity = chatroomRepository.findById(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom not found with id: " + request.roomId()));
            entity.setRoom(relEntity);
        }
        ChatMessage saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    @Override
    public ChatMessageResponse update(UUID id, UpdateChatMessageRequest request) {
        ChatMessage entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChatMessage not found with id: " + id));
        mapper.updateEntity(entity, request);
        if (request.senderId() != null) {
            ChatUser relEntity = chatuserRepository.findById(request.senderId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatUser not found with id: " + request.senderId()));
            entity.setSender(relEntity);
        }
        if (request.roomId() != null) {
            ChatRoom relEntity = chatroomRepository.findById(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom not found with id: " + request.roomId()));
            entity.setRoom(relEntity);
        }
        ChatMessage updated = repository.save(entity);
        return mapper.toResponse(updated);
    }

    @Override
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ChatMessage not found with id: " + id);
        }
        repository.deleteById(id);
    }

    @Override
    public ChatMessageResponse send(UUID id) {
        // Sends a chat message and broadcasts real-time frame via WebSockets
        ChatMessage entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChatMessage not found with id: " + id));

        if (!(entity.getContent() != null && entity.getContent() != "")) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        this.messagingTemplate.convertAndSend("/topic/chat", java.util.Map.of("id", entity.getId(), "content", entity.getContent(), "type", entity.getType(), "createdAt", entity.getCreatedAt()));
        entity = repository.save(entity);

        return mapper.toResponse(entity);
    }

    @Override
    public ChatMessageResponse direct(UUID id) {
        // Sends a direct peer-to-peer message to a targeted user via WebSocket queue
        ChatMessage entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChatMessage not found with id: " + id));

        if (!(entity.getContent() != null && entity.getContent() != "")) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        this.messagingTemplate.convertAndSendToUser(String.valueOf(entity.getRecipientId()), "/queue/messages", java.util.Map.of("id", entity.getId(), "content", entity.getContent(), "senderId", entity.getId()));
        entity = repository.save(entity);

        return mapper.toResponse(entity);
    }

}
