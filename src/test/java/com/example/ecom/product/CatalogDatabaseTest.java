package com.example.ecom.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CatalogDatabaseTest {
    @Autowired ProductService products;
    @Autowired CategoryService categories;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void migrationPreservesLegacySkuAndPriceAsDefaultVariant() {
        ProductDetailResponse p = products.findById(1);
        assertThat(p.sku()).isEqualTo("KEY-MECH-001");
        assertThat(p.price()).isEqualByComparingTo("129.00");
        assertThat(p.variants()).hasSize(1);
        assertThat(p.variants().getFirst().id()).isEqualTo(p.defaultVariantId());
        assertThat(p.variants().getFirst().options()).isEmpty();
        assertThat(p.inventoryQuantity()).isEqualTo(24);
        assertThat(p.variants().getFirst().inventoryQuantity()).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'inventory_quantity'", Integer.class)).isZero();
    }

    @Test void searchAndCategoryFilterCountProductsOnceAndSortByDefaultPrice() {
        long categoryId = categories.create(new CategoryResponse(null, "db-test-apparel", "Apparel")).id();
        ProductDetailResponse shirt = products.create(new ProductRequest("Shirt", "Cotton", "USD", null, 10, true,
                categoryId, null, null, List.of(
                    variant("SHIRT-RED-S", "20.00", "Red", "S"),
                    variant("SHIRT-BLUE-M", "24.00", "Blue", "M", 3)), 0));
        ProductPageResponse page = products.findAll(ProductQuery.from("shirt", String.valueOf(categoryId), "0", "1", "price,desc"));
        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo(shirt.id());
        assertThat(page.items().getFirst().price()).isEqualByComparingTo("20.00");
        assertThat(page.items().getFirst().inventoryQuantity()).isEqualTo(10);
        assertThat(shirt.variants().getFirst().inventoryQuantity()).isEqualTo(10);
        assertThat(shirt.variants().get(1).inventoryQuantity()).isEqualTo(3);
        ProductDetailResponse changed = products.updateVariant(shirt.id(), shirt.variants().get(1).id(),
                variant("SHIRT-BLUE-M", "24.00", "Blue", "M", 7));
        assertThat(changed.variants().get(1).inventoryQuantity()).isEqualTo(7);
        assertThat(changed.inventoryQuantity()).isEqualTo(10);
        ProductDetailResponse unchanged = products.updateVariant(shirt.id(), shirt.variants().get(1).id(),
                variant("SHIRT-BLUE-M", "24.00", "Blue", "M"));
        assertThat(unchanged.variants().get(1).inventoryQuantity()).isEqualTo(7);
        ProductDetailResponse defaultChanged = products.update(shirt.id(), new ProductRequest(
                "Shirt", "Cotton", "USD", null, 12, true, categoryId, null, null, null, null));
        assertThat(defaultChanged.inventoryQuantity()).isEqualTo(12);
        assertThat(defaultChanged.variants().getFirst().inventoryQuantity()).isEqualTo(12);
        assertThat(defaultChanged.variants().get(1).inventoryQuantity()).isEqualTo(7);
        assertThat(products.findAll(ProductQuery.from("blue-m", String.valueOf(categoryId), "0", "20", "name,asc"))
                .totalCount()).isEqualTo(1);
        assertThat(products.findAll(ProductQuery.from("shirt", "1", "0", "20", "name,asc"))
                .totalCount()).isZero();
        assertThatThrownBy(() -> products.addVariant(shirt.id(), variant("SHIRT-RED-S-2", "20.00", " red ", "s")))
                .isInstanceOf(CatalogException.class).hasMessageContaining("Option combination");
        assertThatThrownBy(() -> products.addVariant(shirt.id(), variant("SHIRT-BLUE-M", "20.00", "Green", "L")))
                .isInstanceOf(CatalogException.class).hasMessageContaining("SKU already exists");
    }

    private static VariantRequest variant(String sku, String price, String color, String size) {
        return variant(sku, price, color, size, null);
    }
    private static VariantRequest variant(String sku, String price, String color, String size, Integer inventoryQuantity) {
        return new VariantRequest(sku, new BigDecimal(price), true, inventoryQuantity,
                List.of(new OptionSelection("Color", color), new OptionSelection("Size", size)));
    }
}
