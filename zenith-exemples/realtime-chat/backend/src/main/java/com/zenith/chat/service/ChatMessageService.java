package com.zenith.chat.service;

import com.zenith.chat.dto.*;
import com.zenith.chat.model.*;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

public interface ChatMessageService {
    PageResponse<ChatMessageResponse> getAll(int page, int size, String sort, String direction, String search, MessageType type);
    ChatMessageResponse getById(UUID id);
    ChatMessageResponse create(CreateChatMessageRequest request);
    ChatMessageResponse update(UUID id, UpdateChatMessageRequest request);
    void delete(UUID id);
    ChatMessageResponse send(UUID id);
    ChatMessageResponse direct(UUID id);
}
