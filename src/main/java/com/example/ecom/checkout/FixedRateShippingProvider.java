package com.example.ecom.checkout;

import com.example.ecom.address.ShippingAddress;
import com.example.ecom.cart.CustomerCartResponse;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class FixedRateShippingProvider implements ShippingProvider {
    private static final List<ShippingOption> DOMESTIC = List.of(
            new ShippingOption("STANDARD", "Standard Shipping", new BigDecimal("20000.00"), "IDR", 3, 5),
            new ShippingOption("EXPRESS", "Express Shipping", new BigDecimal("40000.00"), "IDR", 1, 2));
    @Override
    public List<ShippingOption> availableMethods(CustomerCartResponse cart, ShippingAddress address) {
        // The local provider supports Indonesian destinations and IDR carts only; no implicit FX conversion.
        return "IDR".equals(cart.currency()) && "ID".equals(address.countryCode()) ? DOMESTIC : List.of();
    }
}
