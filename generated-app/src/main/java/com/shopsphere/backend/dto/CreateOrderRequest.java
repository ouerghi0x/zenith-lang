package com.shopsphere.backend.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.shopsphere.backend.model.*;

public record CreateOrderRequest(
    @jakarta.validation.constraints.NotBlank(message = "orderNumber cannot be blank")
    String orderNumber,
    @jakarta.validation.constraints.NotNull(message = "totalAmount cannot be null")
    @jakarta.validation.constraints.Positive
    BigDecimal totalAmount,
    @jakarta.validation.constraints.NotNull(message = "status cannot be null")
    OrderStatus status,
    @NotNull(message = "customerId cannot be null")
    UUID customerId
) {}
