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
public class CustomerCartService {
    public static final int MAX_LINE_QUANTITY = 99;
    private static final TypeReference<List<OptionSelection>> OPTIONS = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public CustomerCartService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public CustomerCartResponse get(long customerId) {
        List<CartRow> rows = findCart(customerId, false);
        return rows.isEmpty() ? new CustomerCartResponse(null, null, List.of(), 0,
                BigDecimal.ZERO.setScale(2)) : response(rows.getFirst());
    }

    @Transactional(readOnly = true)
    public CustomerCartResponse requireCheckoutCart(long customerId) {
        CustomerCartResponse cart = get(customerId);
        if (cart.id() == null) throw new CartException(HttpStatus.NOT_FOUND, "Active cart was not found");
        if (cart.items().isEmpty()) throw bad("Cart is empty");
        for (CustomerCartItemResponse item : cart.items()) {
            if (!item.purchasable()) throw new CartException(HttpStatus.CONFLICT, item.unavailableReason());
        }
        return cart;
    }

    @Transactional
    public CustomerCartResponse add(long customerId, CartItemRequest request) {
        if (request == null || request.variantId() == null || request.variantId() <= 0)
            throw bad("variantId is required and must be positive");
        int quantity = validQuantity(request.quantity());
        VariantState variant = requireAvailableVariant(request.variantId());
        UUID newCartId = UUID.randomUUID();
        jdbc.update("INSERT INTO carts (id, customer_id, currency) VALUES (?, ?, ?) " +
                "ON CONFLICT (customer_id) DO NOTHING", newCartId, customerId, variant.currency());
        CartRow cart = findCart(customerId, true).getFirst();
        variant = requireAvailableVariant(request.variantId());
        useCurrency(cart, variant.currency());
        int existing = jdbc.query("SELECT quantity FROM cart_items WHERE cart_id = ? AND variant_id = ?",
                (rs, row) -> rs.getInt(1), cart.id(), request.variantId())
                .stream().findFirst().orElse(0);
        long total = (long) existing + quantity;
        if (total > MAX_LINE_QUANTITY)
            throw new CartException(HttpStatus.CONFLICT, "Cart line cannot exceed " + MAX_LINE_QUANTITY + " units");
        checkStock(total, variant.inventoryQuantity());
        jdbc.update("INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (?, ?, ?) " +
                "ON CONFLICT (cart_id, variant_id) DO UPDATE SET quantity = EXCLUDED.quantity",
                cart.id(), request.variantId(), (int) total);
        return response(new CartRow(cart.id(), variant.currency()));
    }

    @Transactional
    public CustomerCartResponse setQuantity(long customerId, long itemId, CartQuantityRequest request) {
        int quantity = validQuantity(request == null ? null : request.quantity());
        CartRow cart = lockedCartOrNotFound(customerId);
        long variantId = itemVariant(cart.id(), itemId);
        VariantState variant = requireAvailableVariant(variantId);
        if (!variant.currency().equals(cart.currency()))
            throw new CartException(HttpStatus.CONFLICT, "Cart currency differs from product currency");
        checkStock(quantity, variant.inventoryQuantity());
        jdbc.update("UPDATE cart_items SET quantity = ? WHERE cart_id = ? AND id = ?",
                quantity, cart.id(), itemId);
        return response(cart);
    }

    @Transactional
    public void remove(long customerId, long itemId) {
        CartRow cart = lockedCartOrNotFound(customerId);
        if (jdbc.update("DELETE FROM cart_items WHERE cart_id = ? AND id = ?", cart.id(), itemId) == 0)
            throw itemNotFound();
        if (jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id = ?", Integer.class, cart.id()) == 0)
            jdbc.update("UPDATE carts SET currency = NULL WHERE id = ?", cart.id());
    }

    private List<CartRow> findCart(long customerId, boolean lock) {
        return jdbc.query("SELECT id, currency FROM carts WHERE customer_id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new CartRow(rs.getObject(1, UUID.class), rs.getString(2)), customerId);
    }

    private CartRow lockedCartOrNotFound(long customerId) {
        List<CartRow> rows = findCart(customerId, true);
        if (rows.isEmpty()) throw itemNotFound();
        return rows.getFirst();
    }

    private long itemVariant(UUID cartId, long itemId) {
        return jdbc.query("SELECT variant_id FROM cart_items WHERE cart_id = ? AND id = ?",
                (rs, row) -> rs.getLong(1), cartId, itemId).stream().findFirst()
                .orElseThrow(this::itemNotFound);
    }

    private VariantState requireAvailableVariant(long variantId) {
        List<VariantState> matches = jdbc.query("SELECT p.active, v.active, v.inventory_quantity, p.currency " +
                "FROM product_variants v JOIN products p ON p.id = v.product_id WHERE v.id = ?",
                (rs, row) -> new VariantState(rs.getBoolean(1), rs.getBoolean(2),
                        rs.getInt(3), rs.getString(4)), variantId);
        if (matches.isEmpty()) throw new CartException(HttpStatus.NOT_FOUND, "Variant was not found");
        VariantState state = matches.getFirst();
        if (!state.productActive()) throw new CartException(HttpStatus.CONFLICT, "Product is inactive");
        if (!state.variantActive()) throw new CartException(HttpStatus.CONFLICT, "Variant is inactive");
        return state;
    }

    private void useCurrency(CartRow cart, String currency) {
        int count = jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id = ?", Integer.class, cart.id());
        if (count == 0) {
            jdbc.update("UPDATE carts SET currency = ? WHERE id = ?", currency, cart.id());
        } else if (!currency.equals(cart.currency())) {
            throw new CartException(HttpStatus.CONFLICT, "Cart currency differs from product currency");
        }
    }

    private int validQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0 || quantity > MAX_LINE_QUANTITY)
            throw bad("quantity must be an integer from 1 to " + MAX_LINE_QUANTITY);
        return quantity;
    }

    private void checkStock(long quantity, int available) {
        if (quantity > available)
            throw new CartException(HttpStatus.CONFLICT, "Requested quantity exceeds variant stock");
    }

    private CustomerCartResponse response(CartRow cart) {
        List<CustomerCartItemResponse> items = jdbc.query("SELECT ci.id, v.id, v.sku, p.name, v.options_json, " +
                "ci.quantity, v.price, p.currency, p.active, v.active, v.inventory_quantity " +
                "FROM cart_items ci JOIN product_variants v ON v.id = ci.variant_id " +
                "JOIN products p ON p.id = v.product_id WHERE ci.cart_id = ? ORDER BY ci.id",
                (rs, row) -> {
                    int quantity = rs.getInt(6);
                    BigDecimal price = rs.getBigDecimal(7);
                    String currency = rs.getString(8);
                    String reason = quantity <= 0 || quantity > MAX_LINE_QUANTITY ? "Cart line quantity must be from 1 to " + MAX_LINE_QUANTITY :
                            !rs.getBoolean(9) ? "Product is inactive" :
                            !rs.getBoolean(10) ? "Variant is inactive" :
                            !currency.equals(cart.currency()) ? "Cart currency differs from product currency" :
                            quantity > rs.getInt(11) ? "Requested quantity exceeds variant stock" : null;
                    return new CustomerCartItemResponse(rs.getLong(1), rs.getLong(2), rs.getString(3),
                            rs.getString(4), readOptions(rs.getString(5)), quantity, price, currency,
                            price.multiply(BigDecimal.valueOf(quantity)), reason == null, reason);
                }, cart.id());
        long totalQuantity = items.stream().mapToLong(CustomerCartItemResponse::quantity).sum();
        BigDecimal estimated = items.stream().filter(CustomerCartItemResponse::purchasable)
                .map(CustomerCartItemResponse::lineSubtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new CustomerCartResponse(cart.id(), cart.currency(), items, totalQuantity, estimated);
    }

    private List<OptionSelection> readOptions(String json) {
        try { return mapper.readValue(json, OPTIONS); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored variant options", ex); }
    }

    private CartException bad(String message) { return new CartException(HttpStatus.BAD_REQUEST, message); }
    private CartException itemNotFound() { return new CartException(HttpStatus.NOT_FOUND, "Cart item was not found"); }
    private record CartRow(UUID id, String currency) {}
    private record VariantState(boolean productActive, boolean variantActive, int inventoryQuantity, String currency) {}
}
