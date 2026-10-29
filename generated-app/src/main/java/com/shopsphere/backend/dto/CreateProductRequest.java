package com.shopsphere.backend.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.shopsphere.backend.model.*;

public record CreateProductRequest(
    @jakarta.validation.constraints.NotBlank(message = "sku cannot be blank")
    String sku,
    @jakarta.validation.constraints.NotBlank(message = "title cannot be blank")
    @jakarta.validation.constraints.Size(min = 3, max = 150)
    String title,
    String description,
    @jakarta.validation.constraints.NotNull(message = "price cannot be null")
    @jakarta.validation.constraints.Positive
    BigDecimal price,
    @jakarta.validation.constraints.NotNull(message = "stock cannot be null")
    @jakarta.validation.constraints.Min(0)
    Integer stock,
    @NotNull(message = "categoryId cannot be null")
    Long categoryId
) {}
