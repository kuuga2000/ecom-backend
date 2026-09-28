package com.example.ecom.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        String currency,
        String imageUrl,
        int inventoryQuantity,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getImageUrl(),
                product.getInventoryQuantity(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
