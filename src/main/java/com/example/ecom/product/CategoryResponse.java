package com.example.ecom.product;
public record CategoryResponse(Long id, String slug, String name) {
    static CategoryResponse from(Category c) { return new CategoryResponse(c.getId(), c.getSlug(), c.getName()); }
}
