package com.shopsphere.backend.mapper;

import com.shopsphere.backend.entity.Product;
import com.shopsphere.backend.dto.*;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toEntity(CreateProductRequest request) {
        if (request == null) return null;
        Product entity = new Product();
        entity.setSku(request.sku());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPrice(request.price());
        entity.setStock(request.stock());
        entity.setStatus(request.status());
        return entity;
    }

    public void updateEntity(Product entity, UpdateProductRequest request) {
        if (entity == null || request == null) return;
        if (request.sku() != null) {
            entity.setSku(request.sku());
        }
        if (request.name() != null) {
            entity.setName(request.name());
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
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
    }

    public ProductResponse toResponse(Product entity) {
        if (entity == null) return null;
        return new ProductResponse(
            entity.getId(),
            entity.getSku(),
            entity.getName(),
            entity.getDescription(),
            entity.getPrice(),
            entity.getStock(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCategory() != null ? entity.getCategory().getId() : null
        );
    }
}
