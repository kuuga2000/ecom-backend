package com.example.ecom.cart;

import com.example.ecom.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {
    @Mock CartService service;
    MockMvc mvc;
    UUID cartId = UUID.randomUUID();

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void addPassesExplicitVariantIdAndQuantity() throws Exception {
        when(service.add(eq(cartId), any())).thenReturn(new CartResponse(cartId, List.of()));
        mvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                .contentType("application/json").content("{\"variantId\":7,\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(cartId.toString()));
        verify(service).add(cartId, new CartItemRequest(7L, 2));
    }

    @Test void missingVariantIdReturnsBadRequest() throws Exception {
        when(service.add(eq(cartId), any())).thenThrow(new CartException(HttpStatus.BAD_REQUEST,
                "variantId is required and must be positive"));
        mvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                .contentType("application/json").content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail")
                        .value("variantId is required and must be positive"));
    }
}
