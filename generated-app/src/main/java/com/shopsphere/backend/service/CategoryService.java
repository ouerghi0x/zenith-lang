package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.model.*;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

public interface CategoryService {
    PageResponse<CategoryResponse> getAll(int page, int size, String sort, String direction, String search);
    CategoryResponse getById(Long id);
    CategoryResponse create(CreateCategoryRequest request);
    CategoryResponse update(Long id, UpdateCategoryRequest request);
    void delete(Long id);
}
