package com.shopsphere.backend.service.impl;

import com.shopsphere.backend.service.OrderService;
import com.shopsphere.backend.entity.Order;
import com.shopsphere.backend.dto.*;
import com.shopsphere.backend.mapper.OrderMapper;
import com.shopsphere.backend.repository.OrderRepository;
import com.shopsphere.backend.exception.ResourceNotFoundException;
import com.shopsphere.backend.repository.CustomerRepository;
import com.shopsphere.backend.entity.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final CustomerRepository customerRepository;

    public OrderServiceImpl(
        OrderRepository repository,
        OrderMapper mapper,
        CustomerRepository customerRepository
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAll(int page, int size, String sort, String direction, String search) {
        Sort sortObj = "desc".equalsIgnoreCase(direction) ? Sort.by(sort).descending() : Sort.by(sort).ascending();
        Pageable pageable = PageRequest.of(page, size, sortObj);
        Page<Order> entityPage;
        entityPage = repository.findAll(pageable);
        return PageResponse.of(entityPage.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        Order entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return mapper.toResponse(entity);
    }

    @Override
    public OrderResponse create(CreateOrderRequest request) {
        Order entity = mapper.toEntity(request);
        if (request.customerId() != null) {
            Customer relEntity = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + request.customerId()));
            entity.setCustomer(relEntity);
        }
        Order saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    @Override
    public OrderResponse update(Long id, UpdateOrderRequest request) {
        Order entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        mapper.updateEntity(entity, request);
        if (request.customerId() != null) {
            Customer relEntity = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + request.customerId()));
            entity.setCustomer(relEntity);
        }
        Order updated = repository.save(entity);
        return mapper.toResponse(updated);
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Order not found with id: " + id);
        }
        repository.deleteById(id);
    }

    @Override
    public OrderResponse cancel(Long id) {
        Order entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        if (!(entity.getStatus() != OrderStatus.DELIVERED)) {
            throw new IllegalArgumentException("Delivered orders cannot be cancelled");
        }

        entity.setStatus(OrderStatus.CANCELLED);
        entity = repository.save(entity);

        return mapper.toResponse(entity);
    }

    @Override
    public OrderResponse ship(Long id) {
        Order entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        if (!(entity.getStatus() == OrderStatus.PAID)) {
            throw new IllegalArgumentException("Only paid orders can be shipped");
        }

        entity.setStatus(OrderStatus.SHIPPED);
        entity = repository.save(entity);

        return mapper.toResponse(entity);
    }

}
