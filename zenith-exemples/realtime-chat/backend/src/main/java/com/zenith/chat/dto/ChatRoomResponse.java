package com.zenith.chat.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.zenith.chat.model.*;

public record ChatRoomResponse(
    UUID id,
    String name,
    String description,
    LocalDateTime createdAt
) {}
