package com.example.ecom.product;
import java.math.BigDecimal;
import java.util.List;
public record ProductRequest(String name, String description, String currency, String imageUrl,
    Integer inventoryQuantity, Boolean active, Long categoryId, String sku, BigDecimal price,
    List<VariantRequest> variants, Integer defaultVariantIndex) {}
