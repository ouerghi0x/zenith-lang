package com.shopsphere.backend.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.shopsphere.backend.model.*;

public record UpdateProductRequest(
    String sku,
    String title,
    String description,
    BigDecimal price,
    Integer stock,
    Long categoryId
) {}
