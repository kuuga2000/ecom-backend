package com.example.ecom.cart;

import com.example.ecom.product.OptionSelection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {
    private static final TypeReference<List<OptionSelection>> OPTIONS = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public CartService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public CartResponse create() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO carts (id) VALUES (?)", id);
        return new CartResponse(id, List.of());
    }

    @Transactional(readOnly = true)
    public CartResponse get(UUID cartId) {
        requireCart(cartId, false);
        return response(cartId);
    }

    @Transactional
    public CartResponse add(UUID cartId, CartItemRequest request) {
        if (request == null || request.variantId() == null || request.variantId() <= 0)
            throw bad("variantId is required and must be positive");
        int quantity = validQuantity(request.quantity());
        requireCart(cartId, true);
        VariantStock variant = availableVariant(request.variantId());
        int existing = jdbc.query("SELECT quantity FROM cart_items WHERE cart_id = ? AND variant_id = ?",
                (rs, row) -> rs.getInt(1), cartId, request.variantId()).stream().findFirst().orElse(0);
        long total = (long) existing + quantity;
        checkStock(total, variant.inventoryQuantity());
        jdbc.update("INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (?, ?, ?) " +
                "ON CONFLICT (cart_id, variant_id) DO UPDATE SET quantity = EXCLUDED.quantity",
                cartId, request.variantId(), (int) total);
        return response(cartId);
    }

    @Transactional
    public CartResponse setQuantity(UUID cartId, long variantId, CartQuantityRequest request) {
        int quantity = validQuantity(request == null ? null : request.quantity());
        requireCart(cartId, true);
        requireItem(cartId, variantId);
        VariantStock variant = availableVariant(variantId);
        checkStock(quantity, variant.inventoryQuantity());
        jdbc.update("UPDATE cart_items SET quantity = ? WHERE cart_id = ? AND variant_id = ?",
                quantity, cartId, variantId);
        return response(cartId);
    }

    @Transactional
    public CartResponse remove(UUID cartId, long variantId) {
        requireCart(cartId, true);
        if (jdbc.update("DELETE FROM cart_items WHERE cart_id = ? AND variant_id = ?", cartId, variantId) == 0)
            throw new CartException(HttpStatus.NOT_FOUND, "Variant is not in cart");
        return response(cartId);
    }

    private void requireCart(UUID id, boolean lock) {
        String sql = "SELECT id FROM carts WHERE id = ?" + (lock ? " FOR UPDATE" : "");
        if (jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), id).isEmpty())
            throw new CartException(HttpStatus.NOT_FOUND, "Cart was not found");
    }

    private void requireItem(UUID cartId, long variantId) {
        if (jdbc.query("SELECT variant_id FROM cart_items WHERE cart_id = ? AND variant_id = ?",
                (rs, row) -> rs.getLong(1), cartId, variantId).isEmpty())
            throw new CartException(HttpStatus.NOT_FOUND, "Variant is not in cart");
    }

    private VariantStock availableVariant(long id) {
        List<VariantStock> matches = jdbc.query("SELECT p.active, v.active, v.inventory_quantity " +
                "FROM product_variants v JOIN products p ON p.id = v.product_id WHERE v.id = ?",
                (rs, row) -> new VariantStock(rs.getBoolean(1), rs.getBoolean(2), rs.getInt(3)), id);
        if (matches.isEmpty()) throw new CartException(HttpStatus.NOT_FOUND, "Variant was not found");
        VariantStock variant = matches.getFirst();
        if (!variant.productActive() || !variant.active())
            throw new CartException(HttpStatus.CONFLICT, "Variant is not available");
        return variant;
    }

    private int validQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) throw bad("quantity must be a positive integer");
        return quantity;
    }

    private void checkStock(long quantity, int available) {
        if (quantity > available) throw new CartException(HttpStatus.CONFLICT, "Requested quantity exceeds variant stock");
    }

    private CartException bad(String message) { return new CartException(HttpStatus.BAD_REQUEST, message); }

    private CartResponse response(UUID id) {
        List<CartItemResponse> items = jdbc.query("SELECT v.id, p.id, p.name, v.sku, v.options_json, " +
                "v.price, p.currency, ci.quantity FROM cart_items ci " +
                "JOIN product_variants v ON v.id = ci.variant_id " +
                "JOIN products p ON p.id = v.product_id WHERE ci.cart_id = ? ORDER BY v.id",
                (rs, row) -> {
                    BigDecimal price = rs.getBigDecimal(6);
                    int quantity = rs.getInt(8);
                    return new CartItemResponse(rs.getLong(1), rs.getLong(2), rs.getString(3),
                            rs.getString(4), readOptions(rs.getString(5)), price, rs.getString(7),
                            quantity, price.multiply(BigDecimal.valueOf(quantity)));
                }, id);
        return new CartResponse(id, items);
    }

    private List<OptionSelection> readOptions(String json) {
        try { return mapper.readValue(json, OPTIONS); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored variant options", ex); }
    }

    private record VariantStock(boolean productActive, boolean active, int inventoryQuantity) {}
}
