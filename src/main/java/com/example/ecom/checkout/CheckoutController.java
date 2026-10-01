package com.example.ecom.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final CheckoutService checkout;
    public CheckoutController(CheckoutService checkout) { this.checkout = checkout; }
    @GetMapping
    public CheckoutResponse get(@AuthenticationPrincipal Jwt jwt) { return checkout.get(Long.parseLong(jwt.getSubject())); }
    @PutMapping("/shipping-address")
    public CheckoutResponse address(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressSelection request) {
        return checkout.setAddress(Long.parseLong(jwt.getSubject()), request.addressId());
    }
    @GetMapping("/shipping-methods")
    public MethodsResponse methods(@AuthenticationPrincipal Jwt jwt) { return new MethodsResponse(checkout.methods(Long.parseLong(jwt.getSubject()))); }
    @PutMapping("/shipping-method")
    public CheckoutResponse method(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MethodSelection request) {
        return checkout.setMethod(Long.parseLong(jwt.getSubject()), request.code());
    }
    public record AddressSelection(@NotNull @Positive Long addressId) {}
    public record MethodSelection(@NotBlank @Size(max = 50) String code) {}
    public record MethodsResponse(List<ShippingOption> items) {}
}
