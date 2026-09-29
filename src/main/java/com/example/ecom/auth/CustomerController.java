package com.example.ecom.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final AuthService service;
    public CustomerController(AuthService service) { this.service = service; }

    @GetMapping("/me")
    public CustomerResponse me(@AuthenticationPrincipal Jwt token) { return service.me(token); }
}
