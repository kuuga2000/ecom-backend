package com.example.ecom.address;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/me/addresses")
public class AddressController {
    private final AddressService addresses;
    public AddressController(AddressService addresses) { this.addresses = addresses; }
    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) { return addresses.list(Long.parseLong(jwt.getSubject())); }
    @GetMapping("/{id}")
    public AddressResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return addresses.get(Long.parseLong(jwt.getSubject()), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return addresses.create(Long.parseLong(jwt.getSubject()), request);
    }
    @PutMapping("/{id}")
    public AddressResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody AddressRequest request) {
        return addresses.update(Long.parseLong(jwt.getSubject()), id, request);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { addresses.delete(Long.parseLong(jwt.getSubject()), id); }
}
