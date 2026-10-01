package com.example.ecom.checkout;

import com.example.ecom.address.*;
import com.example.ecom.auth.*;
import com.example.ecom.cart.*;
import com.example.ecom.product.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CheckoutDatabaseTest {
    @Autowired AddressService addresses;
    @Autowired CheckoutService checkout;
    @Autowired CustomerCartService carts;
    @Autowired ProductService products;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build(); }

    @Test void addressCrudDefaultsAndOwnership() {
        long owner = customer(), other = customer();
        var first = addresses.create(owner, request(" Home ", "id", true));
        assertThat(first.label()).isEqualTo("Home");
        assertThat(first.countryCode()).isEqualTo("ID");
        var second = addresses.create(owner, request("Office", "ID", true));
        assertThat(addresses.get(owner, first.id()).defaultShipping()).isFalse();
        assertThat(addresses.list(owner)).hasSize(2);
        assertThat(addresses.list(other)).isEmpty();
        assertThatThrownBy(() -> addresses.get(other, first.id())).isInstanceOf(CheckoutException.class);
        assertThatThrownBy(() -> addresses.update(other, first.id(), request("Other", "SG", true))).isInstanceOf(CheckoutException.class);
        assertThatThrownBy(() -> addresses.delete(other, first.id())).isInstanceOf(CheckoutException.class);
        assertThat(addresses.update(owner, first.id(), request("Parents", "SG", true)).label()).isEqualTo("Parents");
        assertThat(addresses.get(owner, second.id()).defaultShipping()).isFalse();
        addresses.delete(owner, first.id());
        assertThat(addresses.list(owner)).hasSize(1).allMatch(a -> !a.defaultShipping());
    }

    @Test void concurrentDefaultsRemainUnique() throws Exception {
        long owner = customer();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return addresses.create(owner, request("A", "ID", true)); });
            var b = pool.submit(() -> { start.await(); return addresses.create(owner, request("B", "ID", true)); });
            start.countDown(); a.get(); b.get();
        }
        assertThat(addresses.list(owner)).hasSize(2).filteredOn(AddressResponse::defaultShipping).hasSize(1);
        long nonDefault = addresses.list(owner).stream().filter(a -> !a.defaultShipping()).findFirst().orElseThrow().id();
        assertThatThrownBy(() -> jdbc.update("UPDATE customer_addresses SET default_shipping = true WHERE id = ?", nonDefault))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void checkoutTotalsSnapshotAndReadiness() {
        long owner = customer();
        cart(owner);
        assertThat(checkout.get(owner).readyForOrder()).isFalse();
        assertThat(checkout.get(owner).grandTotal()).isNull();
        assertThatThrownBy(() -> checkout.methods(owner)).isInstanceOf(CheckoutException.class);
        assertThatThrownBy(() -> checkout.setMethod(owner, "STANDARD")).isInstanceOf(CheckoutException.class);
        var address = addresses.create(owner, request("Home", "ID", true));
        var selected = checkout.setAddress(owner, address.id());
        assertThat(selected.shippingAddress().city()).isEqualTo("Jakarta");
        assertThat(selected.readyForOrder()).isFalse();
        assertThat(checkout.methods(owner)).extracting(ShippingOption::code).containsExactly("STANDARD", "EXPRESS");
        var ready = checkout.setMethod(owner, "STANDARD");
        assertThat(ready.subtotal()).isEqualByComparingTo("350000");
        assertThat(ready.shippingAmount()).isEqualByComparingTo("20000");
        assertThat(ready.grandTotal()).isEqualByComparingTo("370000");
        assertThat(ready.currency()).isEqualTo("IDR");
        assertThat(ready.readyForOrder()).isTrue();
        addresses.update(owner, address.id(), request("Changed", "SG", false));
        addresses.delete(owner, address.id());
        assertThat(checkout.get(owner).shippingAddress().countryCode()).isEqualTo("ID");
        assertThat(checkout.get(owner).readyForOrder()).isTrue();
        var foreign = addresses.create(owner, request("Singapore", "SG", false));
        assertThat(checkout.setAddress(owner, foreign.id()).shippingMethod()).isNull();
        assertThat(checkout.methods(owner)).isEmpty();
        assertThat(checkout.get(owner).readyForOrder()).isFalse();
        assertThatThrownBy(() -> checkout.setMethod(owner, "STANDARD")).isInstanceOf(CheckoutException.class);
        assertThatThrownBy(() -> checkout.setMethod(owner, "UNKNOWN")).isInstanceOf(CheckoutException.class);
    }

    @Test void rejectsEmptyForeignAndStaleCartAndReprices() {
        long owner = customer(), other = customer();
        var address = addresses.create(owner, request("Home", "ID", false));
        assertThatThrownBy(() -> checkout.get(owner)).isInstanceOf(CheckoutException.class);
        cart(owner);
        assertThatThrownBy(() -> checkout.setAddress(owner, addresses.create(other, request("Other", "ID", false)).id()))
                .isInstanceOf(CheckoutException.class);
        checkout.setAddress(owner, address.id()); checkout.setMethod(owner, "STANDARD");
        var item = carts.get(owner).items().getFirst();
        jdbc.update("UPDATE product_variants SET price = 100000 WHERE id = ?", item.variantId());
        assertThat(checkout.get(owner).grandTotal()).isEqualByComparingTo("270000");
        jdbc.update("UPDATE product_variants SET inventory_quantity = 0 WHERE id = ?", item.variantId());
        assertThatThrownBy(() -> checkout.get(owner)).isInstanceOf(CartException.class);
        jdbc.update("UPDATE product_variants SET inventory_quantity = 99, active = false WHERE id = ?", item.variantId());
        assertThatThrownBy(() -> checkout.get(owner)).isInstanceOf(CartException.class);
        jdbc.update("UPDATE product_variants SET active = true WHERE id = ?", item.variantId());
        jdbc.update("UPDATE cart_items SET quantity = 100 WHERE id = ?", item.id());
        assertThatThrownBy(() -> checkout.get(owner)).isInstanceOf(CartException.class);
        for (var line : carts.get(owner).items()) carts.remove(owner, line.id());
        assertThatThrownBy(() -> checkout.setAddress(owner, address.id())).isInstanceOf(CartException.class);
        assertThatThrownBy(() -> checkout.methods(owner)).isInstanceOf(CartException.class);
        assertThatThrownBy(() -> checkout.get(owner)).isInstanceOf(CartException.class);
    }

    @Test void httpSecurityValidationAndBackendPricing() throws Exception {
        String email = "m6-" + UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest("Checkout", email, "long-enough-123"));
        String token = "Bearer " + auth.login(new LoginRequest(email, "long-enough-123")).accessToken();
        long owner = jdbc.queryForObject("SELECT id FROM customers WHERE email = ?", Long.class, email);
        for (String path : List.of("/api/v1/checkout", "/api/v1/checkout/shipping-methods", "/api/v1/customers/me/addresses"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/customers/me/addresses").header("Authorization", token)
                .contentType("application/json").content("{\"recipientName\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        var foreign = addresses.create(customer(), request("Other", "ID", false));
        mvc.perform(get("/api/v1/customers/me/addresses/" + foreign.id()).header("Authorization", token)).andExpect(status().isNotFound());
        cart(owner);
        mvc.perform(put("/api/v1/checkout/shipping-address").header("Authorization", token)
                .contentType("application/json").content("{\"addressId\":" + foreign.id() + "}"))
                .andExpect(status().isNotFound());
        var address = addresses.create(owner, request("Home", "ID", false));
        mvc.perform(put("/api/v1/checkout/shipping-address").header("Authorization", token)
                .contentType("application/json").content("{\"addressId\":" + address.id() + "}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/checkout/shipping-method").header("Authorization", token)
                .contentType("application/json").content("{\"code\":\"STANDARD\",\"amount\":1,\"grandTotal\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.shippingAmount").value(20000))
                .andExpect(jsonPath("$.grandTotal").value(370000)).andExpect(jsonPath("$.readyForOrder").value(true));
    }

    @Test void httpAddressCrudAndInvalidFields() throws Exception {
        String email = "m6-" + UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest("Address user", email, "long-enough-123"));
        String token = "Bearer " + auth.login(new LoginRequest(email, "long-enough-123")).accessToken();
        String path = "/api/v1/customers/me/addresses";
        String body = """
                {"label":"Home","recipientName":"John","phone":"+62 123456","addressLine1":"Jl. Example",
                 "city":"Jakarta","province":"DKI","postalCode":"12345","countryCode":"id","defaultShipping":true}
                """;
        mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        String json = mvc.perform(post(path).header("Authorization", token).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.countryCode").value("ID"))
                .andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path("id").asLong();
        mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(path + "/" + id).header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(put(path + "/" + id).header("Authorization", token).contentType("application/json").content(body.replace("Home", "Office")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Office"));
        for (String invalid : List.of(body.replace("John", " "), body.replace("John", "x".repeat(201)),
                body.replace("\"id\"", "\"123\""), body.replace("+62 123456", ""), body.replace("Jakarta", ""))) {
            mvc.perform(post(path).header("Authorization", token).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/checkout/shipping-address").header("Authorization", token)
                .contentType("application/json").content("{\"addressId\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/checkout/shipping-method").header("Authorization", token)
                .contentType("application/json").content("{\"code\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(path + "/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get(path + "/" + id).header("Authorization", token)).andExpect(status().isNotFound());
    }

    private AddressRequest request(String label, String country, boolean defaultShipping) {
        return new AddressRequest(label, "John Doe", "+62 8123456789", "Jl. Example 10", null,
                "Jakarta", "DKI Jakarta", "12345", country, defaultShipping);
    }
    private long customer() {
        return jdbc.queryForObject("INSERT INTO customers (name, email, password_hash) VALUES ('Checkout', ?, 'test-only') RETURNING id",
                Long.class, "m6-" + UUID.randomUUID() + "@example.com");
    }
    private void cart(long owner) {
        String suffix = UUID.randomUUID().toString();
        var product = products.create(new ProductRequest("M6 " + suffix, "Checkout item", "IDR", null, null, true,
                1L, null, null, List.of(
                new VariantRequest("M6-A-" + suffix, new BigDecimal("150000"), true, 99, List.of(new OptionSelection("Size", "M"))),
                new VariantRequest("M6-B-" + suffix, new BigDecimal("50000"), true, 99, List.of(new OptionSelection("Size", "L")))), 0));
        carts.add(owner, new CartItemRequest(product.variants().getFirst().id(), 2));
        carts.add(owner, new CartItemRequest(product.variants().get(1).id(), 1));
    }
}
