package com.example.ecom.cart;

import org.springframework.http.HttpStatus;

public class CartException extends RuntimeException {
    private final HttpStatus status;

    public CartException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
