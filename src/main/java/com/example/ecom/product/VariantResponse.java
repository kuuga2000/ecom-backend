package com.example.ecom.product;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
public record VariantResponse(Long id, String sku, BigDecimal price, boolean active, List<OptionSelection> options) {
    static VariantResponse from(ProductVariant v, ObjectMapper mapper) {
        try { return new VariantResponse(v.getId(), v.getSku(), v.getPrice(), v.isActive(),
                mapper.readValue(v.getOptionsJson(), new TypeReference<List<OptionSelection>>() {})); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored variant options", ex); }
    }
}
