package com.example.ecom.address;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record AddressRequest(@Size(max = 100) String label,
        @NotBlank @Size(max = 200) String recipientName,
        @NotBlank @Size(max = 40) String phone,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String province,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 2) @Pattern(regexp = "[A-Z]{2}") String countryCode, boolean defaultShipping) {
    public AddressRequest {
        label = trim(label);
        recipientName = trim(recipientName);
        phone = trim(phone);
        addressLine1 = trim(addressLine1);
        addressLine2 = trim(addressLine2);
        city = trim(city);
        province = trim(province);
        postalCode = trim(postalCode);
        countryCode = trim(countryCode);
        if (countryCode != null) countryCode = countryCode.toUpperCase(Locale.ROOT);
    }
    private static String trim(String value) { return value == null ? null : value.strip(); }
    public ShippingAddress shippingAddress() {
        return new ShippingAddress(recipientName, phone, addressLine1, addressLine2, city, province, postalCode, countryCode);
    }
}
