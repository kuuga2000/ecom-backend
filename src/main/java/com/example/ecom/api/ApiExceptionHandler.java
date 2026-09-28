package com.example.ecom.api;

import com.example.ecom.product.InvalidProductQueryException;
import com.example.ecom.product.CatalogException;
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
