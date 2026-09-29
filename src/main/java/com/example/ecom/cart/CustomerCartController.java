package com.example.ecom.cart;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/cart")
public class CustomerCartController {
    private final CustomerCartService service;
    public CustomerCartController(CustomerCartService service) { this.service = service; }

    @GetMapping
    public CustomerCartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED)
    public CustomerCartResponse add(@AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null || !body.keySet().equals(Set.of("variantId", "quantity"))
                || !wholeNumber(body.get("variantId")) || !wholeNumber(body.get("quantity")))
            throw new CartException(HttpStatus.BAD_REQUEST, "variantId and integer quantity are required");
        long variantId = ((Number) body.get("variantId")).longValue();
        long quantity = ((Number) body.get("quantity")).longValue();
        if (quantity < Integer.MIN_VALUE || quantity > Integer.MAX_VALUE)
            throw new CartException(HttpStatus.BAD_REQUEST, "quantity must be an integer from 1 to 99");
        return service.add(Long.parseLong(jwt.getSubject()), new CartItemRequest(variantId, (int) quantity));
    }

    @PatchMapping("/items/{itemId}")
    public CustomerCartResponse setQuantity(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null || !body.keySet().equals(Set.of("quantity")) || !wholeNumber(body.get("quantity")))
            throw new CartException(HttpStatus.BAD_REQUEST, "integer quantity is required");
        long quantity = ((Number) body.get("quantity")).longValue();
        if (quantity < Integer.MIN_VALUE || quantity > Integer.MAX_VALUE)
            throw new CartException(HttpStatus.BAD_REQUEST, "quantity must be an integer from 1 to 99");
        return service.setQuantity(Long.parseLong(jwt.getSubject()), itemId, new CartQuantityRequest((int) quantity));
    }

    @DeleteMapping("/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
        service.remove(Long.parseLong(jwt.getSubject()), itemId);
    }

    private boolean wholeNumber(Object value) { return value instanceof Integer || value instanceof Long; }
}
