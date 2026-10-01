package com.example.ecom.address;

import com.example.ecom.checkout.CheckoutException;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Validated
public class AddressService {
    private final AddressRepository addresses;
    public AddressService(AddressRepository addresses) { this.addresses = addresses; }
    @Transactional(readOnly = true)
    public List<AddressResponse> list(long customer) { return addresses.list(customer); }
    @Transactional(readOnly = true)
    public AddressResponse get(long customer, long id) {
        return addresses.find(customer, id).orElseThrow(() -> new CheckoutException(HttpStatus.NOT_FOUND, "Address was not found"));
    }
    @Transactional
    public AddressResponse create(long customer, @Valid AddressRequest request) {
        addresses.lockCustomer(customer);
        if (request.defaultShipping()) addresses.clearDefault(customer);
        return get(customer, addresses.create(customer, request));
    }
    @Transactional
    public AddressResponse update(long customer, long id, @Valid AddressRequest request) {
        addresses.lockCustomer(customer);
        get(customer, id);
        if (request.defaultShipping()) addresses.clearDefault(customer);
        addresses.update(customer, id, request);
        return get(customer, id);
    }
    @Transactional
    public void delete(long customer, long id) {
        addresses.lockCustomer(customer);
        get(customer, id);
        addresses.delete(customer, id);
    }
}
