package com.example.ecom.cart;

import com.example.ecom.product.OptionSelection;
import java.math.BigDecimal;
import java.util.List;

public record CustomerCartItemResponse(long id, long variantId, String sku, String productName,
        List<OptionSelection> options, int quantity, BigDecimal unitPrice, String currency,
        BigDecimal lineSubtotal, boolean purchasable, String unavailableReason) {}
