package com.example.ecom.product;
import org.springframework.http.HttpStatus;
public class CatalogException extends RuntimeException {
    private final HttpStatus status;
    public CatalogException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
