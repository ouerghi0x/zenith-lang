package com.shopsphere.backend.mapper;

import com.shopsphere.backend.entity.Order;
import com.shopsphere.backend.dto.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public Order toEntity(CreateOrderRequest request) {
        if (request == null) return null;
        Order entity = new Order();
        entity.setOrderNumber(request.orderNumber());
        entity.setTotalAmount(request.totalAmount());
        entity.setStatus(request.status());
        return entity;
    }

    public void updateEntity(Order entity, UpdateOrderRequest request) {
        if (entity == null || request == null) return;
        if (request.orderNumber() != null) {
            entity.setOrderNumber(request.orderNumber());
        }
        if (request.totalAmount() != null) {
            entity.setTotalAmount(request.totalAmount());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
    }

    public OrderResponse toResponse(Order entity) {
        if (entity == null) return null;
        return new OrderResponse(
            entity.getId(),
            entity.getOrderNumber(),
            entity.getTotalAmount(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getCustomer() != null ? entity.getCustomer().getId() : null
        );
    }
}
