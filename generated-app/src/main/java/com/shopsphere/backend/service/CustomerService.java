package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.model.*;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

public interface CustomerService {
    PageResponse<CustomerResponse> getAll(int page, int size, String sort, String direction, String search);
    CustomerResponse getById(UUID id);
    CustomerResponse create(CreateCustomerRequest request);
    CustomerResponse update(UUID id, UpdateCustomerRequest request);
    void delete(UUID id);
}
