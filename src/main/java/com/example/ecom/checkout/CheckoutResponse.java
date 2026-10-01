package com.example.ecom.checkout;

import com.example.ecom.address.ShippingAddress;
import com.example.ecom.cart.CustomerCartItemResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CheckoutResponse(UUID cartId, List<CustomerCartItemResponse> items, ShippingAddress shippingAddress,
        ShippingOption shippingMethod, BigDecimal subtotal, BigDecimal shippingAmount, BigDecimal grandTotal,
        String currency, boolean readyForOrder) {}
