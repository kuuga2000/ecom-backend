package com.example.ecom.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final CustomerRepository customers;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtSettings settings;

    public AuthService(CustomerRepository customers, PasswordEncoder passwords,
            AuthenticationManager authenticationManager, JwtEncoder jwtEncoder, JwtSettings settings) {
        this.customers = customers;
        this.passwords = passwords;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.settings = settings;
    }

    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.name().strip().length() > 200)
            throw bad("Name is required and must be at most 200 characters");
        String email = normalizeEmail(request.email());
        validatePassword(request.password());
        if (customers.existsByEmail(email))
            throw new AuthException(HttpStatus.CONFLICT, "Email is already registered");
        try {
            Customer customer = customers.saveAndFlush(
                    new Customer(request.name().strip(), email, passwords.encode(request.password())));
            return CustomerResponse.from(customer);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = loginEmail(request == null ? null : request.email());
        if (request == null || request.password() == null || request.password().isEmpty())
            throw invalidCredentials();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            throw invalidCredentials();
        }
        Customer customer = customers.findByEmail(email).orElseThrow(this::invalidCredentials);
        if (!customer.isActive()) throw invalidCredentials();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(settings.expirationSeconds());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(customer.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("customer_id", customer.getId())
                .claim("role", customer.getRole().name())
                .claim("token_type", "access")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", expiresAt, settings.expirationSeconds());
    }

    @Transactional(readOnly = true)
    public CustomerResponse me(Jwt token) {
        long id;
        try { id = Long.parseLong(token.getSubject()); }
        catch (RuntimeException ex) { throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid token"); }
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid token"));
        if (!customer.isActive()) throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid token");
        return CustomerResponse.from(customer);
    }

    private String normalizeEmail(String raw) {
        if (raw == null) throw bad("Valid email is required");
        String email = raw.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 320 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw bad("Valid email is required");
        return email;
    }

    private String loginEmail(String raw) {
        if (raw == null) throw invalidCredentials();
        String email = raw.strip().toLowerCase(Locale.ROOT);
        if (email.isBlank()) throw invalidCredentials();
        return email;
    }

    private void validatePassword(String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw bad("Password must be between 12 and 72 UTF-8 bytes");
    }

    private AuthException bad(String message) { return new AuthException(HttpStatus.BAD_REQUEST, message); }
    private AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
