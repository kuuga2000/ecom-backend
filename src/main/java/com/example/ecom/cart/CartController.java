package com.example.ecom.cart;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/carts")
public class CartController {
    private final CartService service;

    public CartController(CartService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CartResponse create() { return service.create(); }

    @GetMapping("/{cartId}")
    public CartResponse get(@PathVariable UUID cartId) { return service.get(cartId); }

    @PostMapping("/{cartId}/items")
    public CartResponse add(@PathVariable UUID cartId, @RequestBody CartItemRequest request) {
        return service.add(cartId, request);
    }

    @PutMapping("/{cartId}/items/{variantId}")
    public CartResponse setQuantity(@PathVariable UUID cartId, @PathVariable long variantId,
            @RequestBody CartQuantityRequest request) {
        return service.setQuantity(cartId, variantId, request);
    }

    @DeleteMapping("/{cartId}/items/{variantId}")
    public CartResponse remove(@PathVariable UUID cartId, @PathVariable long variantId) {
        return service.remove(cartId, variantId);
    }
}
