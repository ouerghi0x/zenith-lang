package com.zenith.chat.service;

import com.zenith.chat.dto.*;
import com.zenith.chat.model.*;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

public interface ChatRoomService {
    PageResponse<ChatRoomResponse> getAll(int page, int size, String sort, String direction, String search, String name);
    ChatRoomResponse getById(UUID id);
    ChatRoomResponse create(CreateChatRoomRequest request);
    ChatRoomResponse update(UUID id, UpdateChatRoomRequest request);
    void delete(UUID id);
}
