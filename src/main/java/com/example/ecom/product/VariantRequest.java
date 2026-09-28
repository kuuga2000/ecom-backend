package com.example.ecom.product;
import java.math.BigDecimal;
import java.util.List;
public record VariantRequest(String sku, BigDecimal price, Boolean active, List<OptionSelection> options) {}
