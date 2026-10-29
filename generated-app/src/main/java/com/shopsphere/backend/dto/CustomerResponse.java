package com.shopsphere.backend.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.shopsphere.backend.model.*;

public record CustomerResponse(
    UUID id,
    String email,
    UserRole role,
    String fullName,
    LocalDateTime createdAt
) {}
