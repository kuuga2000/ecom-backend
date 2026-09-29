package com.example.ecom.cart;

import com.example.ecom.product.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CartDatabaseTest {
    @Autowired CartService carts;
    @Autowired ProductService products;
    @Autowired CategoryService categories;

    @Test void selectedVariantKeepsItsOptionsAndStockLimit() {
        long categoryId = categories.create(new CategoryResponse(null, "cart-test", "Cart test")).id();
        ProductDetailResponse product = products.create(new ProductRequest(
                "Fitting", "Fitting with multiple choices", "USD", null, null, true, categoryId,
                null, null, List.of(
                    new VariantRequest("FITTING-RED-M", new BigDecimal("10.00"), true, 8,
                            List.of(new OptionSelection("Color", "Red"), new OptionSelection("Size", "M"))),
                    new VariantRequest("FITTING-BLACK-6", new BigDecimal("12.00"), true, 3,
                            List.of(new OptionSelection("Diameter", "mm"), new OptionSelection("Size", "6mm"),
                                    new OptionSelection("Color", "Black"))),
                    new VariantRequest("FITTING-BLACK-6-MATTE", new BigDecimal("14.00"), true, 2,
                            List.of(new OptionSelection("Diameter", "mm"), new OptionSelection("Size", "6mm"),
                                    new OptionSelection("Color", "Black"), new OptionSelection("Finish", "Matte")))), 0));
        long selected = product.variants().get(1).id();
        UUID cartId = carts.create().id();

        assertThatThrownBy(() -> carts.add(cartId, new CartItemRequest(null, 1)))
                .isInstanceOfSatisfying(CartException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        CartResponse cart = carts.add(cartId, new CartItemRequest(selected, 2));
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().variantId()).isEqualTo(selected);
        assertThat(cart.items().getFirst().productId()).isEqualTo(product.id());
        assertThat(cart.items().getFirst().options()).extracting(OptionSelection::name)
                .containsExactly("color", "diameter", "size");
        assertThat(cart.items().getFirst().lineTotal()).isEqualByComparingTo("24.00");
        assertThatThrownBy(() -> carts.add(cartId, new CartItemRequest(selected, 2)))
                .isInstanceOfSatisfying(CartException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(carts.get(cartId).items().getFirst().quantity()).isEqualTo(2);
        assertThat(carts.setQuantity(cartId, selected, new CartQuantityRequest(3)).items().getFirst().quantity()).isEqualTo(3);
        assertThat(carts.remove(cartId, selected).items()).isEmpty();

        long fourOptionVariant = product.variants().get(2).id();
        assertThat(carts.add(cartId, new CartItemRequest(fourOptionVariant, 1)).items().getFirst().options()).hasSize(4);
    }
}
