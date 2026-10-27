package com.shopsphere.backend.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.shopsphere.backend.model.*;

public record CreateCustomerRequest(
    @jakarta.validation.constraints.NotBlank(message = "firstName cannot be blank")
    @jakarta.validation.constraints.Size(min = 2, max = 50)
    String firstName,
    @jakarta.validation.constraints.NotBlank(message = "lastName cannot be blank")
    @jakarta.validation.constraints.Size(min = 2, max = 50)
    String lastName,
    @jakarta.validation.constraints.NotBlank(message = "email cannot be blank")
    @jakarta.validation.constraints.Size(max = 100)
    @jakarta.validation.constraints.Email
    String email,
    @jakarta.validation.constraints.NotBlank(message = "password cannot be blank")
    String password,
    @jakarta.validation.constraints.NotNull(message = "role cannot be null")
    UserRole role,
    String phone
) {}
