package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.model.*;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

public interface OrderService {
    PageResponse<OrderResponse> getAll(int page, int size, String sort, String direction, String search, OrderStatus status);
    OrderResponse getById(UUID id);
    OrderResponse create(CreateOrderRequest request);
    OrderResponse update(UUID id, UpdateOrderRequest request);
    void delete(UUID id);
    OrderResponse pay(UUID id);
}
