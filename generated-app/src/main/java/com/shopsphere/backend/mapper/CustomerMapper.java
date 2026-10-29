package com.shopsphere.backend.mapper;

import com.shopsphere.backend.entity.Customer;
import com.shopsphere.backend.dto.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toEntity(CreateCustomerRequest request) {
        if (request == null) return null;
        Customer entity = new Customer();
        entity.setEmail(request.email());
        entity.setPassword(request.password());
        entity.setRole(request.role());
        entity.setFullName(request.fullName());
        return entity;
    }

    public void updateEntity(Customer entity, UpdateCustomerRequest request) {
        if (entity == null || request == null) return;
        if (request.email() != null) {
            entity.setEmail(request.email());
        }
        if (request.password() != null) {
            entity.setPassword(request.password());
        }
        if (request.role() != null) {
            entity.setRole(request.role());
        }
        if (request.fullName() != null) {
            entity.setFullName(request.fullName());
        }
    }

    public CustomerResponse toResponse(Customer entity) {
        if (entity == null) return null;
        return new CustomerResponse(
            entity.getId(),
            entity.getEmail(),
            entity.getRole(),
            entity.getFullName(),
            entity.getCreatedAt()
        );
    }
}
