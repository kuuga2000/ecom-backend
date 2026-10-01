package com.example.ecom.checkout;

import org.springframework.http.HttpStatus;

public class CheckoutException extends RuntimeException {
    private final HttpStatus status;
    public CheckoutException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
