package com.example.ecom.cart;

import java.util.List;
import java.util.UUID;

public record CartResponse(UUID id, List<CartItemResponse> items) {}
