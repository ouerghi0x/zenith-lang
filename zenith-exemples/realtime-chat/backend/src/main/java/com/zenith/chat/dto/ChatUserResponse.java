package com.zenith.chat.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.zenith.chat.model.*;

public record ChatUserResponse(
    UUID id,
    String username,
    String email,
    UserRole role,
    String avatarUrl,
    LocalDateTime createdAt
) {}
