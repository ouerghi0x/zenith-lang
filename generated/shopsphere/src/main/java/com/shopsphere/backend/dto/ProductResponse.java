package com.shopsphere.backend.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.shopsphere.backend.model.*;

public record ProductResponse(
    Long id,
    String sku,
    String name,
    String description,
    BigDecimal price,
    Integer stock,
    ProductStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long categoryId
) {}
