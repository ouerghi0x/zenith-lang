package com.zenith.chat.service.impl;

import com.zenith.chat.service.ChatRoomService;
import com.zenith.chat.entity.ChatRoom;
import com.zenith.chat.dto.*;
import com.zenith.chat.mapper.ChatRoomMapper;
import com.zenith.chat.repository.ChatRoomRepository;
import com.zenith.chat.model.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import com.zenith.chat.exception.ResourceNotFoundException;
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
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository repository;
    private final ChatRoomMapper mapper;

    public ChatRoomServiceImpl(
        ChatRoomRepository repository,
        ChatRoomMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatRoomResponse> getAll(int page, int size, String sort, String direction, String search, String name) {
        Sort sortObj = "desc".equalsIgnoreCase(direction) ? Sort.by(sort).descending() : Sort.by(sort).ascending();
        Pageable pageable = PageRequest.of(page, size, sortObj);
        Specification<ChatRoom> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null) {
                predicates.add(cb.equal(root.get("name"), name));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ChatRoom> entityPage = repository.findAll(spec, pageable);
        return PageResponse.of(entityPage.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomResponse getById(UUID id) {
        ChatRoom entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChatRoom not found with id: " + id));
        return mapper.toResponse(entity);
    }

    @Override
    public ChatRoomResponse create(CreateChatRoomRequest request) {
        ChatRoom entity = mapper.toEntity(request);
        ChatRoom saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    @Override
    public ChatRoomResponse update(UUID id, UpdateChatRoomRequest request) {
        ChatRoom entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChatRoom not found with id: " + id));
        mapper.updateEntity(entity, request);
        ChatRoom updated = repository.save(entity);
        return mapper.toResponse(updated);
    }

    @Override
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ChatRoom not found with id: " + id);
        }
        repository.deleteById(id);
    }

}
