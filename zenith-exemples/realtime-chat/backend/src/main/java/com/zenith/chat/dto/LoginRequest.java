package com.zenith.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Identity/Email cannot be blank")
    String email,
    @NotBlank(message = "Password cannot be blank")
    String password
) {}
