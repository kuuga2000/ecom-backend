package com.example.ecom.address;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class AddressRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<AddressResponse> ROW = (rs, row) -> new AddressResponse(
            rs.getLong("id"),
            rs.getString("label"),
            rs.getString("recipient_name"),
            rs.getString("phone"),
            rs.getString("address_line1"),
            rs.getString("address_line2"),
            rs.getString("city"),
            rs.getString("province"),
            rs.getString("postal_code"),
            rs.getString("country_code"),
            rs.getBoolean("default_shipping"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    public AddressRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lockCustomer(long customer) {
        jdbc.queryForObject("SELECT id FROM customers WHERE id = ? FOR UPDATE", Long.class, customer);
    }
    public List<AddressResponse> list(long customer) {
        return jdbc.query("SELECT * FROM customer_addresses WHERE customer_id = ? ORDER BY id", ROW, customer);
    }
    public Optional<AddressResponse> find(long customer, long id) {
        return jdbc.query("SELECT * FROM customer_addresses WHERE customer_id = ? AND id = ?", ROW, customer, id).stream().findFirst();
    }
    public void clearDefault(long customer) {
        jdbc.update("UPDATE customer_addresses SET default_shipping = false, updated_at = now() WHERE customer_id = ? AND default_shipping", customer);
    }
    public long create(long customer, AddressRequest a) {
        return jdbc.queryForObject("""
                INSERT INTO customer_addresses (customer_id, label, recipient_name, phone, address_line1,
                    address_line2, city, province, postal_code, country_code, default_shipping)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """,
                Long.class, customer, a.label(), a.recipientName(), a.phone(), a.addressLine1(), a.addressLine2(),
                a.city(), a.province(), a.postalCode(), a.countryCode(), a.defaultShipping());
    }
    public void update(long customer, long id, AddressRequest a) {
        jdbc.update("""
                UPDATE customer_addresses SET label = ?, recipient_name = ?, phone = ?, address_line1 = ?,
                    address_line2 = ?, city = ?, province = ?, postal_code = ?, country_code = ?,
                    default_shipping = ?, updated_at = now() WHERE customer_id = ? AND id = ?
                """,
                a.label(), a.recipientName(), a.phone(), a.addressLine1(), a.addressLine2(),
                a.city(), a.province(), a.postalCode(), a.countryCode(), a.defaultShipping(), customer, id);
    }
    public void delete(long customer, long id) {
        jdbc.update("DELETE FROM customer_addresses WHERE customer_id = ? AND id = ?", customer, id);
    }
}
