package com.example.ecom.cart;

import com.example.ecom.product.OptionSelection;
import java.math.BigDecimal;
import java.util.List;

public record CartItemResponse(long variantId, long productId, String productName, String sku,
        List<OptionSelection> options, BigDecimal price, String currency, int quantity,
        BigDecimal lineTotal) {}
