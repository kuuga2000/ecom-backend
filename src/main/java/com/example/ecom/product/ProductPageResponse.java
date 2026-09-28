package com.example.ecom.product;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResponse(
        List<ProductResponse> items,
        int page,
        int size,
        long totalCount
) {
    static ProductPageResponse from(Page<Product> products) {
        return new ProductPageResponse(
                products.getContent().stream().map(ProductResponse::from).toList(),
                products.getNumber(),
                products.getSize(),
                products.getTotalElements()
        );
    }
}
