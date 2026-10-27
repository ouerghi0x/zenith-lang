package com.shopsphere.backend.controller;

import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@CrossOrigin
@Tag(name = "Product", description = "Operations for managing Products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all Products with pagination, sorting, and search")
    public ResponseEntity<PageResponse<ProductResponse>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "id") String sort,
        @RequestParam(defaultValue = "asc") String direction,
        @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.getAll(page, size, sort, direction, search));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a Product by ID")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new Product")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing Product by ID")
    public ResponseEntity<ProductResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a Product by ID")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archives a product from the catalog")
    public ResponseEntity<ProductResponse> archive(@PathVariable Long id) {
        return ResponseEntity.ok(service.archive(id));
    }

}
