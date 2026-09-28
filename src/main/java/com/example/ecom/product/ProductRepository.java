package com.example.ecom.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Override
    @EntityGraph(attributePaths = {"category", "defaultVariant"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"category", "defaultVariant"})
    Optional<Product> findById(Long id);
}
