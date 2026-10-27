package com.shopsphere.backend.controller;

import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

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
        @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.getAll(page, size, sort, direction, search));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a Order by ID")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
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
        @PathVariable Long id,
        @Valid @RequestBody UpdateOrderRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a Order by ID")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancels an order and releases reserved inventory")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @PostMapping("/{id}/ship")
    @Operation(summary = "Marks an order as shipped")
    public ResponseEntity<OrderResponse> ship(@PathVariable Long id) {
        return ResponseEntity.ok(service.ship(id));
    }

}
