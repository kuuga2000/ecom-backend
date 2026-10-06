package com.example.ecom.checkout;

import com.example.ecom.address.ShippingAddress;
import com.example.ecom.cart.CustomerCartResponse;
import java.util.List;

public interface ShippingProvider {
    List<ShippingOption> availableMethods(CustomerCartResponse cart, ShippingAddress address);
}
