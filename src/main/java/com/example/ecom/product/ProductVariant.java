package com.example.ecom.product;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(nullable = false, unique = true, length = 100)
    private String sku;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "options_json", nullable = false, columnDefinition = "text")
    private String optionsJson;
    @Column(name = "option_signature", nullable = false, columnDefinition = "text")
    private String optionSignature;
    protected ProductVariant() {}
    public ProductVariant(Product product, String sku, BigDecimal price, boolean active, String optionsJson, String optionSignature) {
        this.product = product;
        update(sku, price, active, optionsJson, optionSignature);
    }
    public void update(String sku, BigDecimal price, boolean active, String optionsJson, String optionSignature) {
        this.sku = sku; this.price = price; this.active = active; this.optionsJson = optionsJson; this.optionSignature = optionSignature;
    }
    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public String getOptionsJson() { return optionsJson; }
    public String getOptionSignature() { return optionSignature; }
}
