package com.example.ecom.checkout;

import com.example.ecom.address.AddressService;
import com.example.ecom.cart.CustomerCartResponse;
import com.example.ecom.cart.CustomerCartService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
public class CheckoutService {
    private final CheckoutRepository checkout;
    private final CustomerCartService carts;
    private final AddressService addresses;
    private final ShippingProvider shipping;
    public CheckoutService(CheckoutRepository checkout, CustomerCartService carts, AddressService addresses, ShippingProvider shipping) {
        this.checkout = checkout; this.carts = carts; this.addresses = addresses; this.shipping = shipping;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CheckoutResponse get(long customer) {
        var state = state(customer, false);
        return response(carts.requireCheckoutCart(customer), state);
    }
    @Transactional
    public CheckoutResponse setAddress(long customer, long addressId) {
        var state = state(customer, true);
        var cart = carts.requireCheckoutCart(customer);
        var address = addresses.get(customer, addressId).shippingAddress();
        var next = new CheckoutRepository.State(state.cartId(), address, state.methodCode());
        var selected = selected(cart, next);
        checkout.setAddress(state.cartId(), address, selected == null ? null : selected.code());
        return response(cart, next);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ShippingOption> methods(long customer) {
        var state = state(customer, false);
        var cart = carts.requireCheckoutCart(customer);
        requireAddress(state);
        return options(cart, state);
    }
    @Transactional
    public CheckoutResponse setMethod(long customer, String code) {
        var state = state(customer, true);
        var cart = carts.requireCheckoutCart(customer);
        requireAddress(state);
        var method = options(cart, state).stream().filter(option -> option.code().equals(code)).findFirst()
                .orElseThrow(() -> new CheckoutException(HttpStatus.BAD_REQUEST, "Shipping method is unavailable"));
        checkout.setMethod(state.cartId(), method.code());
        return response(cart, new CheckoutRepository.State(state.cartId(), state.address(), method.code()));
    }
    private CheckoutRepository.State state(long customer, boolean lock) {
        return checkout.find(customer, lock).orElseThrow(() -> new CheckoutException(HttpStatus.NOT_FOUND, "Active cart was not found"));
    }
    private void requireAddress(CheckoutRepository.State state) {
        if (state.address() == null) throw new CheckoutException(HttpStatus.BAD_REQUEST, "Select a shipping address first");
    }
    private List<ShippingOption> options(CustomerCartResponse cart, CheckoutRepository.State state) {
        if (state.address() == null) return List.of();
        return shipping.availableMethods(cart, state.address()).stream()
                .filter(option -> Objects.equals(cart.currency(), option.currency()) && option.amount() != null
                        && option.amount().signum() >= 0).toList();
    }
    private ShippingOption selected(CustomerCartResponse cart, CheckoutRepository.State state) {
        return options(cart, state).stream().filter(option -> option.code().equals(state.methodCode())).findFirst().orElse(null);
    }
    private CheckoutResponse response(CustomerCartResponse cart, CheckoutRepository.State state) {
        var method = selected(cart, state);
        var subtotal = cart.estimatedSubtotal();
        return new CheckoutResponse(cart.id(), cart.items(), state.address(), method, subtotal,
                method == null ? null : method.amount(), method == null ? null : subtotal.add(method.amount()),
                cart.currency(), state.address() != null && method != null);
    }
}
