package com.example.ecom.checkout;

import java.math.BigDecimal;

public record ShippingOption(String code, String name, BigDecimal amount, String currency,
        int estimatedDeliveryMinDays, int estimatedDeliveryMaxDays) {}
