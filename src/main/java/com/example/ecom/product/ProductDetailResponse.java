package com.example.ecom.product;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
public record ProductDetailResponse(Long id, String name, String sku, String description, BigDecimal price,
    String currency, String imageUrl, int inventoryQuantity, boolean active, OffsetDateTime createdAt,
    OffsetDateTime updatedAt, CategoryResponse category, Long defaultVariantId, List<VariantResponse> variants) {
    static ProductDetailResponse from(Product p, List<VariantResponse> variants) {
        return new ProductDetailResponse(p.getId(), p.getName(), p.getSku(), p.getDescription(), p.getPrice(),
            p.getCurrency(), p.getImageUrl(), p.getInventoryQuantity(), p.isActive(), p.getCreatedAt(),
            p.getUpdatedAt(), CategoryResponse.from(p.getCategory()), p.getDefaultVariant().getId(), variants);
    }
}
