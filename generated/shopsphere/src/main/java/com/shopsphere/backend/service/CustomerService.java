package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.*;
import java.util.List;

public interface CustomerService {
    PageResponse<CustomerResponse> getAll(int page, int size, String sort, String direction, String search);
    CustomerResponse getById(Long id);
    CustomerResponse create(CreateCustomerRequest request);
    CustomerResponse update(Long id, UpdateCustomerRequest request);
    void delete(Long id);
}
