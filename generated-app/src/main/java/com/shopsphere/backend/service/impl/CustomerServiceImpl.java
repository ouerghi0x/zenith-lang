package com.shopsphere.backend.service.impl;

import com.shopsphere.backend.service.CustomerService;
import com.shopsphere.backend.entity.Customer;
import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.mapper.CustomerMapper;
import com.shopsphere.backend.repository.CustomerRepository;
import com.shopsphere.backend.model.*;
import java.util.UUID;
import java.math.BigDecimal;
import com.shopsphere.backend.exception.ResourceNotFoundException;
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
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    public CustomerServiceImpl(
        CustomerRepository repository,
        CustomerMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getAll(int page, int size, String sort, String direction, String search) {
        Sort sortObj = "desc".equalsIgnoreCase(direction) ? Sort.by(sort).descending() : Sort.by(sort).ascending();
        Pageable pageable = PageRequest.of(page, size, sortObj);
        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Customer> entityPage = repository.findAll(spec, pageable);
        return PageResponse.of(entityPage.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        Customer entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        return mapper.toResponse(entity);
    }

    @Override
    public CustomerResponse create(CreateCustomerRequest request) {
        Customer entity = mapper.toEntity(request);
        Customer saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    @Override
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        mapper.updateEntity(entity, request);
        Customer updated = repository.save(entity);
        return mapper.toResponse(updated);
    }

    @Override
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Customer not found with id: " + id);
        }
        repository.deleteById(id);
    }

}
