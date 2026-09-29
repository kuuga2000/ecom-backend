package com.example.ecom.auth;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, long expiresIn) {}
