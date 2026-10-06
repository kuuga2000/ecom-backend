package com.example.ecom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ApiRouteDatabaseTest {
    @Autowired RequestMappingHandlerMapping routes;

    @Test void allFrontendRoutesHaveTheV1PrefixAndExpectedMethods() {
        Set<String> actual = new TreeSet<>();
        for (var entry : routes.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().isAnnotationPresent(RestController.class)
                    || !entry.getValue().getBeanType().getPackageName().startsWith("com.example.ecom"))
                continue;
            RequestMappingInfo mapping = entry.getKey();
            assertThat(mapping.getMethodsCondition().getMethods()).hasSize(1);
            for (String path : mapping.getPathPatternsCondition().getPatternValues()) {
                assertThat(path).startsWith("/api/v1/");
                actual.add(mapping.getMethodsCondition().getMethods().iterator().next() + " " + path);
            }
        }
        assertThat(actual).containsExactlyInAnyOrder(
                "GET /api/v1/products", "GET /api/v1/products/{id}",
                "POST /api/v1/products", "PUT /api/v1/products/{id}",
                "POST /api/v1/products/{id}/variants", "PUT /api/v1/products/{id}/variants/{variantId}",
                "GET /api/v1/categories", "GET /api/v1/categories/{id}",
                "POST /api/v1/categories", "PUT /api/v1/categories/{id}",
                "POST /api/v1/auth/register", "POST /api/v1/auth/login",
                "GET /api/v1/customers/me",
                "GET /api/v1/customers/me/addresses", "POST /api/v1/customers/me/addresses",
                "GET /api/v1/customers/me/addresses/{id}", "PUT /api/v1/customers/me/addresses/{id}",
                "DELETE /api/v1/customers/me/addresses/{id}",
                "GET /api/v1/checkout", "PUT /api/v1/checkout/shipping-address",
                "GET /api/v1/checkout/shipping-methods", "PUT /api/v1/checkout/shipping-method",
                "POST /api/v1/carts", "GET /api/v1/carts/{cartId}",
                "POST /api/v1/carts/{cartId}/items", "PUT /api/v1/carts/{cartId}/items/{variantId}",
                "DELETE /api/v1/carts/{cartId}/items/{variantId}",
                "GET /api/v1/cart", "POST /api/v1/cart/items",
                "PATCH /api/v1/cart/items/{itemId}", "DELETE /api/v1/cart/items/{itemId}");
    }
}
