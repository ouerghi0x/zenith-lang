package com.zenith.chat.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.zenith.chat.model.*;

public record UpdateChatMessageRequest(
    @jakarta.validation.constraints.Size(min = 1, max = 1000)
    String content,
    MessageType type,
    String recipientId,
    UUID senderId,
    UUID roomId
) {}
