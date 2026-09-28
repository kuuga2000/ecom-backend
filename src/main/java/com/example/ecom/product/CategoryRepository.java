package com.example.ecom.product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsBySlug(String slug);
    Optional<Category> findBySlug(String slug);
}
