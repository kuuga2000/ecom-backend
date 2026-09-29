package com.example.ecom.auth;

import java.time.OffsetDateTime;

public record CustomerResponse(long id, String name, String email, boolean active, CustomerRole role,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail(),
                customer.isActive(), customer.getRole(), customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
