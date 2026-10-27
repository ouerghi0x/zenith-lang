package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.*;
import java.util.List;

public interface CategoryService {
    PageResponse<CategoryResponse> getAll(int page, int size, String sort, String direction, String search);
    CategoryResponse getById(Long id);
    CategoryResponse create(CreateCategoryRequest request);
    CategoryResponse update(Long id, UpdateCategoryRequest request);
    void delete(Long id);
}
