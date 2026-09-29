package com.example.ecom.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class JwtSettings {
    private final SecretKey secretKey;
    private final String issuer;
    private final long expirationSeconds;

    public JwtSettings(@Value("${app.jwt.secret:}") String encodedSecret,
            @Value("${app.jwt.issuer:}") String issuer,
            @Value("${app.jwt.expiration-seconds:900}") long expirationSeconds) {
        if (encodedSecret == null || encodedSecret.isBlank())
            throw new IllegalStateException("JWT_SECRET is required and must be a base64-encoded random key");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encodedSecret); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("JWT_SECRET must be valid base64", ex); }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        if (issuer == null || issuer.isBlank()) throw new IllegalStateException("JWT_ISSUER is required");
        if (expirationSeconds < 60 || expirationSeconds > 3600)
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be between 60 and 3600");
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public SecretKey secretKey() { return secretKey; }
    public String issuer() { return issuer; }
    public long expirationSeconds() { return expirationSeconds; }
}
