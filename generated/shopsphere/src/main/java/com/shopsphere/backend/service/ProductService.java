package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.*;
import java.util.List;

public interface ProductService {
    PageResponse<ProductResponse> getAll(int page, int size, String sort, String direction, String search);
    ProductResponse getById(Long id);
    ProductResponse create(CreateProductRequest request);
    ProductResponse update(Long id, UpdateProductRequest request);
    void delete(Long id);
    ProductResponse archive(Long id);
}
