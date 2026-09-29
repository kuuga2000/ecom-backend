package com.example.ecom.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomerCartResponse(UUID id, String currency, List<CustomerCartItemResponse> items,
        long totalQuantity, BigDecimal estimatedSubtotal) {}
