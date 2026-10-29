package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.model.*;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

public interface ProductService {
    PageResponse<ProductResponse> getAll(int page, int size, String sort, String direction, String search, BigDecimal price);
    ProductResponse getById(Long id);
    ProductResponse create(CreateProductRequest request);
    ProductResponse update(Long id, UpdateProductRequest request);
    void delete(Long id);
}
