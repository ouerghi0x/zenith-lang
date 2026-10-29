package com.shopsphere.backend.mapper;

import com.shopsphere.backend.entity.Product;
import com.shopsphere.backend.dto.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toEntity(CreateProductRequest request) {
        if (request == null) return null;
        Product entity = new Product();
        entity.setSku(request.sku());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setPrice(request.price());
        entity.setStock(request.stock());
        return entity;
    }

    public void updateEntity(Product entity, UpdateProductRequest request) {
        if (entity == null || request == null) return;
        if (request.sku() != null) {
            entity.setSku(request.sku());
        }
        if (request.title() != null) {
            entity.setTitle(request.title());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.price() != null) {
            entity.setPrice(request.price());
        }
        if (request.stock() != null) {
            entity.setStock(request.stock());
        }
    }

    public ProductResponse toResponse(Product entity) {
        if (entity == null) return null;
        return new ProductResponse(
            entity.getId(),
            entity.getSku(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getPrice(),
            entity.getStock(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCategory() != null ? entity.getCategory().getId() : null
        );
    }
}
