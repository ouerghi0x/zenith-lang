package com.zenith.chat.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.zenith.chat.model.*;

public record ChatMessageResponse(
    UUID id,
    String content,
    MessageType type,
    String recipientId,
    LocalDateTime createdAt,
    UUID senderId,
    UUID roomId
) {}
