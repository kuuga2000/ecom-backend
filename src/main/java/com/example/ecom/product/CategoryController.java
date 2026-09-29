package com.example.ecom.product;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }
    @GetMapping public List<CategoryResponse> all() { return service.all(); }
    @GetMapping("/{id}") public CategoryResponse one(@PathVariable long id) { return service.one(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public CategoryResponse create(@RequestBody CategoryResponse request) { return service.create(request); }
    @PutMapping("/{id}") public CategoryResponse update(@PathVariable long id, @RequestBody CategoryResponse request) { return service.update(id, request); }
}
