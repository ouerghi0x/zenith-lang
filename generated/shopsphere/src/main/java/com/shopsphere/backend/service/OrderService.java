package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.*;
import java.util.List;

public interface OrderService {
    PageResponse<OrderResponse> getAll(int page, int size, String sort, String direction, String search);
    OrderResponse getById(Long id);
    OrderResponse create(CreateOrderRequest request);
    OrderResponse update(Long id, UpdateOrderRequest request);
    void delete(Long id);
    OrderResponse cancel(Long id);
    OrderResponse ship(Long id);
}
