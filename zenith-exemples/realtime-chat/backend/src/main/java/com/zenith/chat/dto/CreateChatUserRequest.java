package com.zenith.chat.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.zenith.chat.model.*;

public record CreateChatUserRequest(
    @jakarta.validation.constraints.NotBlank(message = "username cannot be blank")
    @jakarta.validation.constraints.Size(min = 3, max = 30)
    String username,
    @jakarta.validation.constraints.NotBlank(message = "email cannot be blank")
    @jakarta.validation.constraints.Email(message = "Invalid email format")
    String email,
    @jakarta.validation.constraints.NotBlank(message = "password cannot be blank")
    @jakarta.validation.constraints.Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    String password,
    @jakarta.validation.constraints.NotNull(message = "role cannot be null")
    UserRole role,
    String avatarUrl
) {}
