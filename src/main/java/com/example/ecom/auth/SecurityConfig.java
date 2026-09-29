package com.example.ecom.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class SecurityConfig {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(CustomerRepository customers) {
        return email -> {
            Customer customer = customers.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(customer.getEmail())
                    .password(customer.getPasswordHash())
                    .disabled(!customer.isActive())
                    .roles(customer.getRole().name())
                    .build();
        };
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder passwords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(JwtSettings settings) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(settings.secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtSettings settings) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(settings.secretKey())
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            String subject = jwt.getSubject();
            Object customerId = jwt.getClaim("customer_id");
            String role = jwt.getClaimAsString("role");
            boolean validId = false;
            try {
                long id = Long.parseLong(subject);
                validId = id > 0 && customerId instanceof Number number && number.longValue() == id;
            } catch (RuntimeException ignored) {}
            if (validId && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && ("CUSTOMER".equals(role) || "ADMIN".equals(role))
                    && "access".equals(jwt.getClaimAsString("token_type")))
                return OAuth2TokenValidatorResult.success();
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required JWT claims are invalid", null));
        };
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.issuer()), requiredClaims));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CustomerRepository customers) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            long id = Long.parseLong(jwt.getSubject());
            Customer customer = customers.findById(id).orElseThrow(SecurityConfig::invalidToken);
            if (!customer.isActive() || !customer.getRole().name().equals(jwt.getClaimAsString("role")))
                throw invalidToken();
            return List.of(new SimpleGrantedAuthority("ROLE_" + customer.getRole().name()));
        });
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**",
                                "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers("/api/carts", "/api/carts/**").permitAll()
                        .requestMatchers("/api/products", "/api/products/**",
                                "/api/categories", "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers("/api/customers/me").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                problem(request, response, 401, "Unauthorized", "Authentication required or token invalid"))
                        .accessDeniedHandler((request, response, exception) ->
                                problem(request, response, 403, "Forbidden", "Insufficient permissions")))
                .build();
    }

    private static OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Invalid access token", null));
    }

    private static void problem(HttpServletRequest request, HttpServletResponse response, int status, String title, String detail)
            throws java.io.IOException {
        response.setStatus(status);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"https://example.com/problems/auth\",\"title\":\""
                + title + "\",\"status\":" + status + ",\"detail\":\"" + detail
                + "\",\"instance\":" + JSON.writeValueAsString(request.getRequestURI()) + "}");
    }
}
