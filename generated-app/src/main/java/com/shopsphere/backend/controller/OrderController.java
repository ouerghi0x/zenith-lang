package com.shopsphere.backend.controller;

import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.service.OrderService;
import com.shopsphere.backend.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

import java.util.UUID;
import java.math.BigDecimal;
@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin
@Tag(name = "Order", description = "Operations for managing Orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all Orders with pagination, sorting, and search")
    public ResponseEntity<PageResponse<OrderResponse>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "id") String sort,
        @RequestParam(defaultValue = "asc") String direction,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) OrderStatus status
    ) {
        return ResponseEntity.ok(service.getAll(page, size, sort, direction, search, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a Order by ID")
    public ResponseEntity<OrderResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new Order")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing Order by ID")
    public ResponseEntity<OrderResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateOrderRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a Order by ID")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Pays for an order")
    public ResponseEntity<OrderResponse> pay(@PathVariable UUID id) {
        return ResponseEntity.ok(service.pay(id));
    }

}
