package com.example.ecom.product;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }
    @GetMapping public ProductPageResponse findAll(@RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId, @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size, @RequestParam(defaultValue = "name,asc") String sort) {
        return service.findAll(ProductQuery.from(q, categoryId, page, size, sort));
    }
    @GetMapping("/{id}") public ProductDetailResponse findById(@PathVariable long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ProductDetailResponse create(@RequestBody ProductRequest request) { return service.create(request); }
    @PutMapping("/{id}") public ProductDetailResponse update(@PathVariable long id, @RequestBody ProductRequest request) { return service.update(id, request); }
    @PostMapping("/{id}/variants") @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailResponse addVariant(@PathVariable long id, @RequestBody VariantRequest request) { return service.addVariant(id, request); }
    @PutMapping("/{id}/variants/{variantId}")
    public ProductDetailResponse updateVariant(@PathVariable long id, @PathVariable long variantId, @RequestBody VariantRequest request) {
        return service.updateVariant(id, variantId, request);
    }
}
