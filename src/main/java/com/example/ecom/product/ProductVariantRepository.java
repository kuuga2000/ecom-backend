package com.example.ecom.product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductIdOrderById(long productId);
    Optional<ProductVariant> findByIdAndProductId(long id, long productId);
    boolean existsBySkuAndIdNot(String sku, long id);
    boolean existsByOptionSignatureAndProductIdAndIdNot(String signature, long productId, long id);
}
