package com.example.ecom.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class AuthDatabaseTest {
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired JwtSettings settings;
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test void registrationLoginAndMeUseSafeProfileAndPasswordHash() throws Exception {
        String email = uniqueEmail();
        String password = "correct-horse-123";
        var registration = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" Alice \",\"email\":\" " + email.toUpperCase() + " \",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andReturn();
        assertThat(registration.getResponse().getContentAsString()).doesNotContain("password", "hash");
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM customers WHERE email = ?", String.class, email);
        assertThat(storedHash).isNotEqualTo(password).startsWith("$2");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Other\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing-" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password"));

        String token = login(email.toUpperCase(), password);
        mvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test void registrationRejectsInvalidInputAndClientSelectedRole() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"email\":\"" + email + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"email\":\"bad\",\"password\":\"long-enough-123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"email\":\"" + email + "\",\"password\":\"long-enough-123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test void databaseEnforcesNormalizedEmailUniqueness() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"email\":\"" + email + "\",\"password\":\"long-enough-123\"}"))
                .andExpect(status().isCreated());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO customers (name, email, password_hash) VALUES (?, ?, ?)",
                "Duplicate", email, "not-a-real-hash"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void tokenValidationAndRoutePermissions() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Customer\",\"email\":\"" + email + "\",\"password\":\"long-enough-123\"}"))
                .andExpect(status().isCreated());
        String token = login(email, "long-enough-123");
        long id = jdbc.queryForObject("SELECT id FROM customers WHERE email = ?", Long.class, email);

        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories/1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/carts/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customers/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.instance").value("/api/v1/customers/me"));
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"security-test\",\"name\":\"Security test\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"security-test\",\"name\":\"Security test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.instance").value("/api/v1/categories"));
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/categories/1").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/products/1").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products/1/variants").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/products/1/variants/1").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + signed(
                id, "CUSTOMER", settings.issuer(), Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60), encoder))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + signed(
                id, "CUSTOMER", "wrong-issuer", Instant.now(),
                Instant.now().plusSeconds(300), encoder))).andExpect(status().isUnauthorized());
        JwtEncoder wrongKey = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(new byte[32], "HmacSHA256")));
        mvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + signed(
                id, "CUSTOMER", settings.issuer(), Instant.now(),
                Instant.now().plusSeconds(300), wrongKey))).andExpect(status().isUnauthorized());

        jdbc.update("UPDATE customers SET role = 'ADMIN' WHERE id = ?", id);
        mvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        String adminToken = login(email, "long-enough-123");
        mvc.perform(post("/api/v1/categories").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"security-test-" + id + "\",\"name\":\"Security test\"}"))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE customers SET active = false WHERE id = ?", id);
        mvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(settings.expirationSeconds()))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = mapper.readTree(body);
        return response.get("accessToken").asText();
    }

    private String signed(long id, String role, String issuer, Instant issued, Instant expires, JwtEncoder signer) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(Long.toString(id))
                .issuedAt(issued).expiresAt(expires)
                .claim("customer_id", id).claim("role", role).claim("token_type", "access").build();
        return signer.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String uniqueEmail() {
        return "m4-test-" + UUID.randomUUID() + "@example.com";
    }
}
