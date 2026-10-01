package com.example.ecom.checkout;

import com.example.ecom.address.ShippingAddress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CheckoutRepository {
    private final JdbcTemplate jdbc;
    public CheckoutRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<State> find(long customer, boolean lock) {
        return jdbc.query("SELECT * FROM carts WHERE customer_id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new State(rs.getObject("id", UUID.class),
                        rs.getString("shipping_recipient_name") == null ? null : new ShippingAddress(
                        rs.getString("shipping_recipient_name"),
                        rs.getString("shipping_phone"),
                        rs.getString("shipping_address_line1"),
                        rs.getString("shipping_address_line2"),
                        rs.getString("shipping_city"),
                        rs.getString("shipping_province"),
                        rs.getString("shipping_postal_code"),
                        rs.getString("shipping_country_code")),
                        rs.getString("shipping_method_code")), customer).stream().findFirst();
    }
    public void setAddress(UUID cart, ShippingAddress a, String method) {
        jdbc.update("""
                UPDATE carts SET shipping_recipient_name = ?, shipping_phone = ?, shipping_address_line1 = ?,
                    shipping_address_line2 = ?, shipping_city = ?, shipping_province = ?, shipping_postal_code = ?,
                    shipping_country_code = ?, shipping_method_code = ? WHERE id = ?
                """,
                a.recipientName(), a.phone(), a.addressLine1(), a.addressLine2(),
                a.city(), a.province(), a.postalCode(), a.countryCode(), method, cart);
    }
    public void setMethod(UUID cart, String code) {
        jdbc.update("UPDATE carts SET shipping_method_code = ? WHERE id = ?", code, cart);
    }
    public record State(UUID cartId, ShippingAddress address, String methodCode) {}
}
