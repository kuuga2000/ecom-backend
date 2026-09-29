package com.example.ecom.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;


    @Column(nullable = false, length = 2_000)
    private String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToOne
    @JoinColumn(name = "default_variant_id")
    private ProductVariant defaultVariant;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "image_url", length = 1_000)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
        // Required by JPA, which materializes entities through a no-argument constructor.
    }

    public Product(String name, String description, String currency, String imageUrl, boolean active, Category category) {
        update(name, description, currency, imageUrl, active, category);
        this.createdAt = this.updatedAt;
    }

    public void update(String name, String description, String currency, String imageUrl, boolean active, Category category) {
        this.name = name; this.description = description; this.currency = currency; this.imageUrl = imageUrl;
        this.active = active; this.category = category;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setDefaultVariant(ProductVariant variant) { this.defaultVariant = variant; }
    public Category getCategory() { return category; }
    public ProductVariant getDefaultVariant() { return defaultVariant; }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return defaultVariant.getSku();
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return defaultVariant.getPrice();
    }

    public String getCurrency() {
        return currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getInventoryQuantity() {
        return defaultVariant.getInventoryQuantity();
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
