package com.zenith.chat.controller;

import com.zenith.chat.dto.*;
import com.zenith.chat.service.ChatRoomService;
import com.zenith.chat.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/chat_rooms")
@CrossOrigin
@Tag(name = "ChatRoom", description = "Operations for managing ChatRooms")
public class ChatRoomController {

    private final ChatRoomService service;

    public ChatRoomController(ChatRoomService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all ChatRooms with pagination, sorting, and search")
    @PreAuthorize("permitAll()")
    public ResponseEntity<PageResponse<ChatRoomResponse>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "id") String sort,
        @RequestParam(defaultValue = "asc") String direction,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok(service.getAll(page, size, sort, direction, search, name));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ChatRoom by ID")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ChatRoomResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new ChatRoom")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatRoomResponse> create(@Valid @RequestBody CreateChatRoomRequest request) {
        ChatRoomResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing ChatRoom by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatRoomResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateChatRoomRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a ChatRoom by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
