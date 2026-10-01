package com.example.ecom.checkout;

import com.example.ecom.address.*;
import com.example.ecom.cart.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutServiceTest {
    private final CheckoutRepository repository = mock(CheckoutRepository.class);
    private final CustomerCartService carts = mock(CustomerCartService.class);
    private final AddressService addresses = mock(AddressService.class);
    private final ShippingProvider provider = mock(ShippingProvider.class);
    private final CheckoutService service = new CheckoutService(repository, carts, addresses, provider);
    private final ShippingAddress address = new ShippingAddress("John", "+62 123", "Street", null, "Jakarta", "DKI", "12345", "ID");
    private final UUID cartId = UUID.randomUUID();
    private final CustomerCartResponse cart = new CustomerCartResponse(cartId, "IDR", List.of(), 2, new BigDecimal("300000.00"));

    @Test void invalidatesMethodWhenProviderChangesAndUsesCurrentBackendQuote() {
        when(repository.find(1, false)).thenReturn(Optional.of(new CheckoutRepository.State(cartId, address, "STANDARD")));
        when(carts.requireCheckoutCart(1)).thenReturn(cart);
        when(provider.availableMethods(cart, address)).thenReturn(List.of(
                new ShippingOption("STANDARD", "Standard", new BigDecimal("25000"), "IDR", 3, 5)));
        assertThat(service.get(1).grandTotal()).isEqualByComparingTo("325000");
        when(provider.availableMethods(cart, address)).thenReturn(List.of());
        var invalid = service.get(1);
        assertThat(invalid.readyForOrder()).isFalse();
        assertThat(invalid.shippingMethod()).isNull();
        assertThat(invalid.shippingAmount()).isNull();
        assertThat(invalid.grandTotal()).isNull();
        verify(repository, never()).setMethod(any(), any());
    }

    @Test void refusesCurrencyMismatchAndNegativeProviderPrice() {
        when(repository.find(1, false)).thenReturn(Optional.of(new CheckoutRepository.State(cartId, address, "STANDARD")));
        when(carts.requireCheckoutCart(1)).thenReturn(cart);
        when(provider.availableMethods(cart, address)).thenReturn(List.of(
                new ShippingOption("STANDARD", "Standard", BigDecimal.ONE, "USD", 3, 5)));
        assertThat(service.get(1).readyForOrder()).isFalse();
        when(provider.availableMethods(cart, address)).thenReturn(List.of(
                new ShippingOption("STANDARD", "Standard", BigDecimal.ONE.negate(), "IDR", 3, 5)));
        assertThat(service.get(1).readyForOrder()).isFalse();
    }

    @Test void fixedRateProviderHasExplicitDestinationAndCurrencySupport() {
        var local = new FixedRateShippingProvider();
        assertThat(local.availableMethods(cart, address)).extracting(ShippingOption::amount)
                .containsExactly(new BigDecimal("20000.00"), new BigDecimal("40000.00"));
        var usd = new CustomerCartResponse(cartId, "USD", List.of(), 2, BigDecimal.TEN);
        assertThat(local.availableMethods(usd, address)).isEmpty();
        var foreign = new ShippingAddress("John", "+65 123", "Street", null, "Singapore", "Singapore", "123456", "SG");
        assertThat(local.availableMethods(cart, foreign)).isEmpty();
    }
}
