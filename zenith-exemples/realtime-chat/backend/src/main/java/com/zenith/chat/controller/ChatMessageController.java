package com.zenith.chat.controller;

import com.zenith.chat.dto.*;
import com.zenith.chat.service.ChatMessageService;
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
@RequestMapping("/api/v1/chat_messages")
@CrossOrigin
@Tag(name = "ChatMessage", description = "Operations for managing ChatMessages")
public class ChatMessageController {

    private final ChatMessageService service;

    public ChatMessageController(ChatMessageService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all ChatMessages with pagination, sorting, and search")
    @PreAuthorize("permitAll()")
    public ResponseEntity<PageResponse<ChatMessageResponse>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "id") String sort,
        @RequestParam(defaultValue = "asc") String direction,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) MessageType type
    ) {
        return ResponseEntity.ok(service.getAll(page, size, sort, direction, search, type));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ChatMessage by ID")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ChatMessageResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new ChatMessage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageResponse> create(@Valid @RequestBody CreateChatMessageRequest request) {
        ChatMessageResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing ChatMessage by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateChatMessageRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a ChatMessage by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Sends a chat message and broadcasts real-time frame via WebSockets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageResponse> send(@PathVariable UUID id) {
        return ResponseEntity.ok(service.send(id));
    }

    @PostMapping("/{id}/direct")
    @Operation(summary = "Sends a direct peer-to-peer message to a targeted user via WebSocket queue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageResponse> direct(@PathVariable UUID id) {
        return ResponseEntity.ok(service.direct(id));
    }

}
