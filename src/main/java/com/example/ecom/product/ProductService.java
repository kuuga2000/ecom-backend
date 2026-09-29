package com.example.ecom.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
public class ProductService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CategoryService categories;
    private final ObjectMapper mapper = new ObjectMapper();
    public ProductService(ProductRepository products, ProductVariantRepository variants, CategoryService categories) {
        this.products = products; this.variants = variants; this.categories = categories;
    }

    @Transactional(readOnly = true) public ProductPageResponse findAll(ProductQuery query) {
        Specification<Product> spec = (root, cq, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (query.categoryId() != null) predicates.add(cb.equal(root.get("category").get("id"), query.categoryId()));
            if (query.hasSearchTerm()) {
                String term = "%" + query.searchTerm().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                Subquery<Long> sub = cq.subquery(Long.class);
                var variant = sub.from(ProductVariant.class);
                sub.select(variant.get("id")).where(cb.equal(variant.get("product"), root),
                        cb.like(cb.lower(variant.get("sku")), term, '\\'));
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), term, '\\'), cb.exists(sub)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Sort.Order first = query.pageable().getSort().iterator().next();
        String field = first.getProperty().equals("price") ? "defaultVariant.price" : "name";
        Pageable pageable = PageRequest.of(query.pageable().getPageNumber(), query.pageable().getPageSize(),
                Sort.by(new Sort.Order(first.getDirection(), field), new Sort.Order(Sort.Direction.ASC, "id")));
        Page<Product> page = products.findAll(spec, pageable);
        return ProductPageResponse.from(page);
    }

    @Transactional(readOnly = true) public ProductDetailResponse findById(long id) { return detail(get(id)); }
    private Product get(long id) { return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id)); }
    private ProductDetailResponse detail(Product p) {
        return ProductDetailResponse.from(p, variants.findByProductIdOrderById(p.getId()).stream()
                .map(v -> VariantResponse.from(v, mapper)).toList());
    }

    public ProductDetailResponse create(ProductRequest request) {
        checkProduct(request);
        Category category = categories.get(request.categoryId());
        List<VariantRequest> requested = request.variants();
        if (requested == null || requested.isEmpty()) {
            requested = List.of(new VariantRequest(request.sku(), request.price(), true, request.inventoryQuantity(), List.of()));
        } else if (request.sku() != null || request.price() != null) {
            throw bad("Use variants or legacy sku/price, not both");
        }
        int defaultIndex = request.defaultVariantIndex() == null ? 0 : request.defaultVariantIndex();
        if (defaultIndex < 0 || defaultIndex >= requested.size()) throw bad("Invalid defaultVariantIndex");
        VariantRequest selected = requested.get(defaultIndex);
        if (selected == null) throw bad("Variant is required");
        if (request.inventoryQuantity() != null && selected.inventoryQuantity() != null
                && !request.inventoryQuantity().equals(selected.inventoryQuantity()))
            throw bad("Product inventoryQuantity must match the default variant");
        if (request.inventoryQuantity() != null && selected.inventoryQuantity() == null) {
            requested = new ArrayList<>(requested);
            requested.set(defaultIndex, new VariantRequest(selected.sku(), selected.price(), selected.active(),
                    request.inventoryQuantity(), selected.options()));
        }
        Product product = products.saveAndFlush(new Product(request.name().trim(), request.description().trim(),
                request.currency().trim().toUpperCase(Locale.ROOT), request.imageUrl(), request.active(), category));
        List<ProductVariant> saved = new ArrayList<>();
        for (VariantRequest item : requested) saved.add(saveVariant(product, item, null));
        product.setDefaultVariant(saved.get(defaultIndex));
        products.flush();
        return detail(product);
    }

    public ProductDetailResponse update(long id, ProductRequest request) {
        Product product = get(id);
        checkProduct(request);
        if (request.variants() != null || request.defaultVariantIndex() != null) throw bad("Update variants through variant endpoints");
        product.update(request.name().trim(), request.description().trim(), request.currency().trim().toUpperCase(Locale.ROOT),
                request.imageUrl(), request.active(), categories.get(request.categoryId()));
        if (request.sku() != null || request.price() != null || request.inventoryQuantity() != null) {
            if ((request.sku() == null) != (request.price() == null)) throw bad("sku and price must be provided together");
            ProductVariant v = product.getDefaultVariant();
            saveVariant(product, new VariantRequest(request.sku() == null ? v.getSku() : request.sku(),
                    request.price() == null ? v.getPrice() : request.price(), v.isActive(),
                    request.inventoryQuantity(), readOptions(v)), v);
        }
        return detail(product);
    }

    public ProductDetailResponse addVariant(long productId, VariantRequest request) {
        Product product = get(productId);
        saveVariant(product, request, null);
        return detail(product);
    }
    public ProductDetailResponse updateVariant(long productId, long variantId, VariantRequest request) {
        Product product = get(productId);
        ProductVariant variant = variants.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new CatalogException(HttpStatus.NOT_FOUND, "Variant " + variantId + " was not found"));
        saveVariant(product, request, variant);
        return detail(product);
    }
    private ProductVariant saveVariant(Product product, VariantRequest request, ProductVariant current) {
        if (request == null || request.sku() == null || request.sku().isBlank() || request.sku().trim().length() > 100)
            throw bad("Valid SKU is required");
        if (request.price() == null || request.price().signum() < 0 || request.price().scale() > 2 || request.price().precision() > 12)
            throw bad("price must be nonnegative with at most two decimal places");
        if (request.active() == null) throw bad("active is required");
        if (request.inventoryQuantity() != null && request.inventoryQuantity() < 0)
            throw bad("inventoryQuantity must be zero or greater");
        int inventoryQuantity = request.inventoryQuantity() == null
                ? (current == null ? 0 : current.getInventoryQuantity()) : request.inventoryQuantity();
        long currentId = current == null ? -1 : current.getId();
        String sku = request.sku().trim();
        if (variants.existsBySkuAndIdNot(sku, currentId)) throw conflict("SKU already exists");
        List<OptionSelection> options = normalizeOptions(request.options());
        String signature = json(options);
        if (variants.existsByOptionSignatureAndProductIdAndIdNot(signature, product.getId(), currentId))
            throw conflict("Option combination already exists for product");
        ProductVariant variant = current == null ? new ProductVariant(product, sku, request.price(), request.active(), inventoryQuantity, signature, signature) : current;
        if (current != null) variant.update(sku, request.price(), request.active(), inventoryQuantity, signature, signature);
        return variants.saveAndFlush(variant);
    }
    private List<OptionSelection> normalizeOptions(List<OptionSelection> options) {
        if (options == null) throw bad("options are required; use [] for a default variant");
        Set<String> keys = new HashSet<>();
        List<OptionSelection> normalized = new ArrayList<>();
        for (OptionSelection option : options) {
            if (option == null || option.name() == null || option.value() == null || option.name().isBlank() || option.value().isBlank())
                throw bad("Option name and value are required");
            String name = option.name().trim().toLowerCase(Locale.ROOT);
            String value = option.value().trim().toLowerCase(Locale.ROOT);
            if (name.length() > 100 || value.length() > 200) throw bad("Option name or value is too long");
            if (!keys.add(name)) throw bad("Duplicate option key: " + name);
            normalized.add(new OptionSelection(name, value));
        }
        normalized.sort(Comparator.comparing(OptionSelection::name));
        return normalized;
    }
    private String json(List<OptionSelection> options) {
        try { return mapper.writeValueAsString(options); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private List<OptionSelection> readOptions(ProductVariant variant) { return VariantResponse.from(variant, mapper).options(); }
    private void checkProduct(ProductRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.name().trim().length() > 200 ||
                request.description() == null || request.description().trim().length() > 2000 ||
                request.currency() == null || !request.currency().trim().matches("[A-Za-z]{3}") ||
                request.categoryId() == null || request.inventoryQuantity() != null && request.inventoryQuantity() < 0 ||
                request.active() == null || request.imageUrl() != null && request.imageUrl().length() > 1000)
            throw bad("Invalid product fields");
    }
    private CatalogException bad(String detail) { return new CatalogException(HttpStatus.BAD_REQUEST, detail); }
    private CatalogException conflict(String detail) { return new CatalogException(HttpStatus.CONFLICT, detail); }
}
