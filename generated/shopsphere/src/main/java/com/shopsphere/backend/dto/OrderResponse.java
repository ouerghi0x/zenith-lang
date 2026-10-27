package com.shopsphere.backend.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.shopsphere.backend.model.*;

public record OrderResponse(
    Long id,
    String orderNumber,
    BigDecimal totalAmount,
    OrderStatus status,
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long customerId
) {}
