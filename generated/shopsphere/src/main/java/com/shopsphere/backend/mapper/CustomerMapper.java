package com.shopsphere.backend.mapper;

import com.shopsphere.backend.entity.Customer;
import com.shopsphere.backend.dto.*;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toEntity(CreateCustomerRequest request) {
        if (request == null) return null;
        Customer entity = new Customer();
        entity.setFirstName(request.firstName());
        entity.setLastName(request.lastName());
        entity.setEmail(request.email());
        entity.setPassword(request.password());
        entity.setRole(request.role());
        entity.setPhone(request.phone());
        return entity;
    }

    public void updateEntity(Customer entity, UpdateCustomerRequest request) {
        if (entity == null || request == null) return;
        if (request.firstName() != null) {
            entity.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            entity.setLastName(request.lastName());
        }
        if (request.email() != null) {
            entity.setEmail(request.email());
        }
        if (request.password() != null) {
            entity.setPassword(request.password());
        }
        if (request.role() != null) {
            entity.setRole(request.role());
        }
        if (request.phone() != null) {
            entity.setPhone(request.phone());
        }
    }

    public CustomerResponse toResponse(Customer entity) {
        if (entity == null) return null;
        return new CustomerResponse(
            entity.getId(),
            entity.getFirstName(),
            entity.getLastName(),
            entity.getEmail(),
            entity.getRole(),
            entity.getPhone(),
            entity.getCreatedAt()
        );
    }
}
