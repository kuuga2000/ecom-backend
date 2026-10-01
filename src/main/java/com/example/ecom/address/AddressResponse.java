package com.example.ecom.address;

import java.time.Instant;

public record AddressResponse(long id,
        String label,
        String recipientName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        String countryCode,
        boolean defaultShipping, Instant createdAt, Instant updatedAt) {
    public ShippingAddress shippingAddress() {
        return new ShippingAddress(recipientName, phone, addressLine1, addressLine2, city, province, postalCode, countryCode);
    }
}
