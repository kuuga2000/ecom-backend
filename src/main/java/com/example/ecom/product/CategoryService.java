package com.example.ecom.product;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
@Service
@Transactional
public class CategoryService {
    private final CategoryRepository repository;
    public CategoryService(CategoryRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true) public List<CategoryResponse> all() {
        return repository.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(CategoryResponse::from).toList();
    }
    @Transactional(readOnly = true) public CategoryResponse one(long id) { return CategoryResponse.from(get(id)); }
    Category get(long id) { return repository.findById(id).orElseThrow(() -> new CatalogException(HttpStatus.NOT_FOUND, "Category " + id + " was not found")); }
    public CategoryResponse create(CategoryResponse request) {
        if (request == null || request.slug() == null || request.name() == null) throw new CatalogException(HttpStatus.BAD_REQUEST, "slug and name are required");
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        String name = request.name().trim();
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 100 || name.isEmpty() || name.length() > 200)
            throw new CatalogException(HttpStatus.BAD_REQUEST, "Invalid category slug or name");
        if (repository.existsBySlug(slug)) throw new CatalogException(HttpStatus.CONFLICT, "Category slug already exists");
        return CategoryResponse.from(repository.saveAndFlush(new Category(slug, name)));
    }
    public CategoryResponse update(long id, CategoryResponse request) {
        Category category = get(id);
        if (request == null || request.name() == null || request.name().isBlank() || request.name().trim().length() > 200)
            throw new CatalogException(HttpStatus.BAD_REQUEST, "Valid category name is required");
        if (request.slug() != null && !request.slug().equals(category.getSlug()))
            throw new CatalogException(HttpStatus.BAD_REQUEST, "Category slug is immutable");
        category.update(request.name().trim());
        return CategoryResponse.from(category);
    }
}
