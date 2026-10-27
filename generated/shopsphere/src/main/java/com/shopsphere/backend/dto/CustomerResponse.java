package com.shopsphere.backend.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.shopsphere.backend.model.*;

public record CustomerResponse(
    Long id,
    String firstName,
    String lastName,
    String email,
    UserRole role,
    String phone,
    LocalDateTime createdAt
) {}
