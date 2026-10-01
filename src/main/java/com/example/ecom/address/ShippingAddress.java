package com.example.ecom.address;

public record ShippingAddress(String recipientName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        String countryCode) {}
