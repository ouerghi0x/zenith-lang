package com.zenith.chat.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.zenith.chat.model.*;

public record CreateChatRoomRequest(
    @jakarta.validation.constraints.NotBlank(message = "name cannot be blank")
    @jakarta.validation.constraints.Size(min = 2, max = 50)
    String name,
    String description
) {}
