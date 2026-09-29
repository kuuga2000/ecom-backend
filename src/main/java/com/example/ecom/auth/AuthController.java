package com.example.ecom.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null || !Set.of("name", "email", "password").containsAll(body.keySet())
                || !(body.get("name") instanceof String)
                || !(body.get("email") instanceof String)
                || !(body.get("password") instanceof String))
            throw new AuthException(HttpStatus.BAD_REQUEST, "Only name, email, and password are accepted");
        return service.register(new RegisterRequest((String) body.get("name"),
                (String) body.get("email"), (String) body.get("password")));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody(required = false) LoginRequest request) {
        return service.login(request);
    }
}
