package com.example.ecom.api;

import com.example.ecom.product.InvalidProductQueryException;
import com.example.ecom.product.CatalogException;
import com.example.ecom.cart.CartException;
import com.example.ecom.auth.AuthException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.example.ecom.product.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(com.example.ecom.checkout.CheckoutException.class)
    ProblemDetail handleCheckout(com.example.ecom.checkout.CheckoutException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setTitle("Checkout request failed");
        problem.setType(URI.create("https://example.com/problems/checkout"));
        return problem;
    }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            jakarta.validation.ConstraintViolationException.class})
    ProblemDetail handleValidation(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request fields failed validation");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://example.com/problems/validation"));
        return problem;
    }

    @ExceptionHandler(AuthException.class)
    ProblemDetail handleAuth(AuthException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setTitle(exception.getStatus().value() == 409 ? "Account conflict" :
                exception.getStatus().value() == 401 ? "Unauthorized" : "Invalid account request");
        problem.setType(URI.create("https://example.com/problems/auth"));
        return problem;
    }

    @ExceptionHandler(CartException.class)
    ProblemDetail handleCart(CartException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setTitle(exception.getStatus().value() == 404 ? "Cart resource not found" :
                exception.getStatus().value() == 409 ? "Cart conflict" : "Invalid cart request");
        problem.setType(URI.create("https://example.com/problems/cart"));
        return problem;
    }

    @ExceptionHandler(CatalogException.class)
    ProblemDetail handleCatalog(CatalogException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setTitle(exception.getStatus().value() == 404 ? "Resource not found" :
                exception.getStatus().value() == 409 ? "Catalog conflict" : "Invalid catalog request");
        problem.setType(URI.create("https://example.com/problems/catalog"));
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConstraint(DataIntegrityViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Catalog uniqueness or reference constraint violated");
        problem.setTitle("Catalog conflict");
        problem.setType(URI.create("https://example.com/problems/catalog"));
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleInvalidBody(HttpMessageNotReadableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid JSON request body");
        problem.setTitle("Invalid catalog request");
        problem.setType(URI.create("https://example.com/problems/catalog"));
        return problem;
    }


    @ExceptionHandler(InvalidProductQueryException.class)
    ProblemDetail handleInvalidProductQuery(InvalidProductQueryException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid product query");
        problem.setType(URI.create("https://example.com/problems/invalid-product-query"));
        problem.setProperty("parameter", exception.getParameter());
        problem.setProperty("rejectedValue", exception.getRejectedValue());
        return problem;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleProductNotFound(ProductNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Product not found");
        problem.setType(URI.create("https://example.com/problems/product-not-found"));
        problem.setProperty("productId", exception.getProductId());
        return problem;
    }
}
