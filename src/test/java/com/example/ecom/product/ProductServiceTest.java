package com.example.ecom.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository products;
    @Mock ProductVariantRepository variants;
    @Mock CategoryService categories;
    @Test void listUsesOneProductPageAndRetainsDistinctCount() {
        Product product = mock(Product.class);
        when(product.getSku()).thenReturn("BLUE-M");
        when(products.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(product), call.getArgument(1), 3));
        ProductPageResponse response = new ProductService(products, variants, categories)
                .findAll(ProductQuery.from("blue", "2", "1", "1", "price,desc"));
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().sku()).isEqualTo("BLUE-M");
        verify(products).findAll(any(Specification.class), argThat((Pageable page) ->
                page.getSort().getOrderFor("defaultVariant.price") != null));
    }
    @Test void rejectsDuplicateOptionKeysAfterNormalization() {
        Product product = mock(Product.class);
        when(products.findById(1L)).thenReturn(java.util.Optional.of(product));
        ProductService service = new ProductService(products, variants, categories);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.addVariant(1L,
                new VariantRequest("SKU-1", new BigDecimal("1.00"), true, 2,
                        List.of(new OptionSelection(" Color ", "Red"), new OptionSelection("color", "Blue")))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("Duplicate option key");
    }
    @Test void rejectsNegativeVariantInventory() {
        Product product = mock(Product.class);
        when(products.findById(1L)).thenReturn(java.util.Optional.of(product));
        ProductService service = new ProductService(products, variants, categories);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.addVariant(1L,
                new VariantRequest("SKU-3", new BigDecimal("1.00"), true, -1, List.of())))
                .isInstanceOf(CatalogException.class).hasMessageContaining("inventoryQuantity");
    }
    @Test void rejectsDuplicateCombinationDespiteDifferentSkuAndOrder() {
        Product product = mock(Product.class);
        when(products.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(product.getId()).thenReturn(1L);
        when(variants.existsByOptionSignatureAndProductIdAndIdNot(anyString(), eq(1L), eq(-1L))).thenReturn(true);
        ProductService service = new ProductService(products, variants, categories);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.addVariant(1L,
                new VariantRequest("SKU-2", new BigDecimal("1.00"), true, 2,
                        List.of(new OptionSelection("Size", " M "), new OptionSelection("COLOR", "Red")))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("Option combination already exists");
    }
}
