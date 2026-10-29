package com.shopsphere.backend.mapper;

import com.shopsphere.backend.entity.Category;
import com.shopsphere.backend.dto.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public Category toEntity(CreateCategoryRequest request) {
        if (request == null) return null;
        Category entity = new Category();
        entity.setName(request.name());
        entity.setDescription(request.description());
        return entity;
    }

    public void updateEntity(Category entity, UpdateCategoryRequest request) {
        if (entity == null || request == null) return;
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
    }

    public CategoryResponse toResponse(Category entity) {
        if (entity == null) return null;
        return new CategoryResponse(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getCreatedAt()
        );
    }
}
