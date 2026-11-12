package com.zenith.chat.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.zenith.chat.model.*;

public record CreateChatMessageRequest(
    @jakarta.validation.constraints.NotBlank(message = "content cannot be blank")
    @jakarta.validation.constraints.Size(min = 1, max = 1000)
    String content,
    @jakarta.validation.constraints.NotNull(message = "type cannot be null")
    MessageType type,
    String recipientId,
    @NotNull(message = "senderId cannot be null")
    UUID senderId,
    UUID roomId
) {}
