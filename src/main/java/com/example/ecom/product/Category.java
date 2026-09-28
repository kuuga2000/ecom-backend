package com.example.ecom.product;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String slug;
    @Column(nullable = false, length = 200)
    private String name;
    protected Category() {}
    public Category(String slug, String name) { this.slug = slug; this.name = name; }
    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public void update(String name) { this.name = name; }
}
