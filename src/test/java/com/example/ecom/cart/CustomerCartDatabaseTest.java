package com.example.ecom.cart;

import com.example.ecom.auth.*;
import com.example.ecom.product.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.*;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CustomerCartDatabaseTest {
    @Autowired CustomerCartService carts;
    @Autowired ProductService products;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired JwtEncoder encoder;
    @Autowired JwtSettings settings;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test void emptyReadDoesNotWriteAndCartFlowsTrackExactVariantsAndCurrentPrices() {
        long customer = customer();
        int before = jdbc.queryForObject("SELECT count(*) FROM carts", Integer.class);
        CustomerCartResponse empty = carts.get(customer);
        assertThat(empty.id()).isNull();
        assertThat(empty.items()).isEmpty();
        assertThat(empty.estimatedSubtotal()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM carts", Integer.class)).isEqualTo(before);

        ProductDetailResponse product = product(8, 8);
        long red = product.variants().getFirst().id();
        long blue = product.variants().get(1).id();
        CustomerCartResponse first = carts.add(customer, new CartItemRequest(red, 2));
        assertThat(first.items()).hasSize(1);
        assertThat(first.items().getFirst().options()).extracting(OptionSelection::value)
                .containsExactly("red", "m");
        assertThat(first.estimatedSubtotal()).isEqualByComparingTo("20.00");
        long redItemId = first.items().getFirst().id();

        CustomerCartResponse both = carts.add(customer, new CartItemRequest(blue, 1));
        assertThat(both.items()).hasSize(2);
        assertThat(both.totalQuantity()).isEqualTo(3);
        assertThat(both.estimatedSubtotal()).isEqualByComparingTo("32.00");
        assertThat(carts.add(customer, new CartItemRequest(red, 1)).items().getFirst().quantity()).isEqualTo(3);
        assertThat(carts.setQuantity(customer, redItemId, new CartQuantityRequest(1))
                .items().getFirst().quantity()).isEqualTo(1);

        products.updateVariant(product.id(), red, new VariantRequest(product.variants().getFirst().sku(),
                new BigDecimal("15.00"), true, 8,
                List.of(new OptionSelection("color", "red"), new OptionSelection("size", "m"))));
        assertThat(carts.get(customer).estimatedSubtotal()).isEqualByComparingTo("27.00");
        jdbc.update("UPDATE product_variants SET inventory_quantity = 0 WHERE id = ?", red);
        assertThat(carts.get(customer).items().getFirst().unavailableReason())
                .isEqualTo("Requested quantity exceeds variant stock");
        assertThat(carts.get(customer).estimatedSubtotal()).isEqualByComparingTo("12.00");
        jdbc.update("UPDATE product_variants SET inventory_quantity = 8 WHERE id = ?", red);

        jdbc.update("UPDATE product_variants SET active = false WHERE id = ?", blue);
        CustomerCartResponse inactive = carts.get(customer);
        assertThat(inactive.items().get(1).purchasable()).isFalse();
        assertThat(inactive.items().get(1).unavailableReason()).isEqualTo("Variant is inactive");
        assertThat(inactive.estimatedSubtotal()).isEqualByComparingTo("15.00");
        assertThat(inactive.totalQuantity()).isEqualTo(2);
        carts.remove(customer, inactive.items().get(1).id());
        carts.remove(customer, redItemId);
        assertThat(carts.get(customer).items()).isEmpty();
        assertThat(carts.get(customer).currency()).isNull();
    }

    @Test void validatesVariantStatusStockQuantityAndOwnership() {
        long owner = customer();
        long other = customer();
        ProductDetailResponse product = product(2, 3);
        long red = product.variants().getFirst().id();
        assertCartError(HttpStatus.NOT_FOUND, () -> carts.add(owner, new CartItemRequest(999999999L, 1)));
        assertCartError(HttpStatus.BAD_REQUEST, () -> carts.add(owner, new CartItemRequest(null, 1)));
        assertCartError(HttpStatus.BAD_REQUEST, () -> carts.add(owner, new CartItemRequest(red, 0)));
        assertCartError(HttpStatus.BAD_REQUEST, () -> carts.add(owner, new CartItemRequest(red, 100)));
        assertCartError(HttpStatus.CONFLICT, () -> carts.add(owner, new CartItemRequest(red, 3)));
        assertThat(carts.get(owner).id()).isNull();

        CustomerCartResponse added = carts.add(owner, new CartItemRequest(red, 2));
        long itemId = added.items().getFirst().id();
        assertCartError(HttpStatus.CONFLICT, () -> carts.add(owner, new CartItemRequest(red, 1)));
        assertCartError(HttpStatus.CONFLICT, () -> carts.setQuantity(owner, itemId, new CartQuantityRequest(3)));
        assertCartError(HttpStatus.NOT_FOUND, () -> carts.setQuantity(other, itemId, new CartQuantityRequest(1)));
        assertCartError(HttpStatus.NOT_FOUND, () -> carts.remove(other, itemId));
        assertThat(carts.get(other).items()).isEmpty();

        jdbc.update("UPDATE products SET active = false WHERE id = ?", product.id());
        assertThat(carts.get(owner).items().getFirst().unavailableReason()).isEqualTo("Product is inactive");
        assertThat(carts.get(owner).estimatedSubtotal()).isEqualByComparingTo("0.00");
        assertCartError(HttpStatus.CONFLICT, () -> carts.add(owner, new CartItemRequest(product.variants().get(1).id(), 1)));
        assertCartError(HttpStatus.CONFLICT, () -> carts.setQuantity(owner, itemId, new CartQuantityRequest(1)));
        carts.remove(owner, itemId);
    }

    @Test void handlesTwoConcurrentFirstAddsAndRepeatedConcurrentAdds() throws Exception {
        long customer = customer();
        ProductDetailResponse product = product(99, 99);
        long variant = product.variants().getFirst().id();
        concurrentAdds(customer, variant, 8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM carts WHERE customer_id = ?", Integer.class, customer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items ci JOIN carts c ON c.id = ci.cart_id " +
                "WHERE c.customer_id = ? AND ci.variant_id = ?", Integer.class, customer, variant)).isEqualTo(1);
        assertThat(carts.get(customer).items().getFirst().quantity()).isEqualTo(8);
        concurrentAdds(customer, variant, 8);
        assertThat(carts.get(customer).items().getFirst().quantity()).isEqualTo(16);
        assertThat(carts.add(customer, new CartItemRequest(variant, 83))
                .items().getFirst().quantity()).isEqualTo(99);
        assertCartError(HttpStatus.CONFLICT, () -> carts.add(customer, new CartItemRequest(variant, 1)));
    }

    @Test void httpRequiresJwtAndRejectsPartialOrFractionalSelections() throws Exception {
        String email = "m5-test-" + UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest("Cart user", email, "long-enough-123"));
        String token = auth.login(new LoginRequest(email, "long-enough-123")).accessToken();
        ProductDetailResponse product = product(5, 5);
        long variant = product.variants().getFirst().id();

        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        JwtClaimsSet expired = JwtClaimsSet.builder().issuer(settings.issuer())
                .subject(Long.toString(jdbc.queryForObject("SELECT id FROM customers WHERE email = ?", Long.class, email)))
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(60))
                .claim("customer_id", jdbc.queryForObject("SELECT id FROM customers WHERE email = ?", Long.class, email))
                .claim("role", "CUSTOMER").claim("token_type", "access").build();
        String expiredToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), expired)).getTokenValue();
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"productId\":" + product.id() + ",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"variantId\":" + variant + ",\"quantity\":1.5}"))
                .andExpect(status().isBadRequest());
        String addedJson = mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"variantId\":" + variant + ",\"quantity\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].variantId").value(variant))
                .andReturn().getResponse().getContentAsString();
        var addedCart = new ObjectMapper().readTree(addedJson);
        long itemId = addedCart.path("items").get(0).path("id").asLong();
        String customerCartId = addedCart.path("id").asText();
        mvc.perform(get("/api/carts/{id}", customerCartId)).andExpect(status().isNotFound());
        String otherEmail = "m5-test-" + UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest("Other cart user", otherEmail, "long-enough-123"));
        String otherToken = auth.login(new LoginRequest(otherEmail, "long-enough-123")).accessToken();
        mvc.perform(patch("/api/cart/items/{itemId}", itemId)
                .header("Authorization", "Bearer " + otherToken).contentType("application/json")
                .content("{\"quantity\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/cart/items/{itemId}", itemId)
                .header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(patch("/api/cart/items/{itemId}", itemId)
                .header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(delete("/api/cart/items/{itemId}", itemId)
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
    }

    private void concurrentAdds(long customer, long variant, int count) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(count)) {
            List<Future<CustomerCartResponse>> futures = java.util.stream.IntStream.range(0, count)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return carts.add(customer, new CartItemRequest(variant, 1));
                    })).toList();
            start.countDown();
            for (Future<CustomerCartResponse> future : futures) future.get();
        }
    }

    private long customer() {
        String email = "m5-test-" + UUID.randomUUID() + "@example.com";
        return jdbc.queryForObject("INSERT INTO customers (name, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class, "Cart user", email, "test-only-hash");
    }

    private ProductDetailResponse product(int redStock, int blueStock) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return products.create(new ProductRequest("M5 Item " + suffix, "Cotton shirt", "USD", null,
                null, true, 1L, null, null, List.of(
                    new VariantRequest("RED-M-" + suffix, new BigDecimal("10.00"), true, redStock,
                            List.of(new OptionSelection("Color", "Red"), new OptionSelection("Size", "M"))),
                    new VariantRequest("BLUE-S-" + suffix, new BigDecimal("12.00"), true, blueStock,
                            List.of(new OptionSelection("Color", "Blue"), new OptionSelection("Size", "S")))), 0));
    }

    private void assertCartError(HttpStatus status, ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CartException.class,
                error -> assertThat(error.getStatus()).isEqualTo(status));
    }
}
