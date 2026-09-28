package com.example.ecom.product;

public class InvalidProductQueryException extends RuntimeException {

    private final String parameter;
    private final String rejectedValue;

    public InvalidProductQueryException(String parameter, String rejectedValue, String message) {
        super(message);
        this.parameter = parameter;
        this.rejectedValue = rejectedValue;
    }

    public String getParameter() {
        return parameter;
    }

    public String getRejectedValue() {
        return rejectedValue;
    }
}
