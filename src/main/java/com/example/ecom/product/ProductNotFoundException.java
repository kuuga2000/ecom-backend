package com.example.ecom.product;

public class ProductNotFoundException extends RuntimeException {

    private final long productId;

    public ProductNotFoundException(long productId) {
        super("Product " + productId + " was not found");
        this.productId = productId;
    }

    public long getProductId() {
        return productId;
    }
}
