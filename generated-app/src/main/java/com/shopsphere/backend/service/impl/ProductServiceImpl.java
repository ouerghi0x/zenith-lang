package com.shopsphere.backend.service.impl;

import com.shopsphere.backend.service.ProductService;
import com.shopsphere.backend.entity.Product;
import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.mapper.ProductMapper;
import com.shopsphere.backend.repository.ProductRepository;
import com.shopsphere.backend.model.*;
import java.util.UUID;
import java.math.BigDecimal;
import com.shopsphere.backend.exception.ResourceNotFoundException;
import com.shopsphere.backend.repository.CategoryRepository;
import com.shopsphere.backend.entity.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final CategoryRepository categoryRepository;

    public ProductServiceImpl(
        ProductRepository repository,
        ProductMapper mapper,
        CategoryRepository categoryRepository
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAll(int page, int size, String sort, String direction, String search, BigDecimal price) {
        Sort sortObj = "desc".equalsIgnoreCase(direction) ? Sort.by(sort).descending() : Sort.by(sort).ascending();
        Pageable pageable = PageRequest.of(page, size, sortObj);
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (price != null) {
                predicates.add(cb.equal(root.get("price"), price));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> entityPage = repository.findAll(spec, pageable);
        return PageResponse.of(entityPage.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapper.toResponse(entity);
    }

    @Override
    public ProductResponse create(CreateProductRequest request) {
        Product entity = mapper.toEntity(request);
        if (request.categoryId() != null) {
            Category relEntity = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
            entity.setCategory(relEntity);
        }
        Product saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    @Override
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        mapper.updateEntity(entity, request);
        if (request.categoryId() != null) {
            Category relEntity = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
            entity.setCategory(relEntity);
        }
        Product updated = repository.save(entity);
        return mapper.toResponse(updated);
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        repository.deleteById(id);
    }

}
