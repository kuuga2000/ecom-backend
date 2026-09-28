package com.example.ecom.product;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

public record ProductQuery(String searchTerm, Long categoryId, Pageable pageable) {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "price");

    public static ProductQuery from(String q, String pageValue, String sizeValue, String sortValue) {
        return from(q, null, pageValue, sizeValue, sortValue);
    }

    public static ProductQuery from(String q, String categoryValue, String pageValue, String sizeValue, String sortValue) {
        Long categoryId = null;
        if (categoryValue != null) {
            try { categoryId = Long.valueOf(categoryValue); }
            catch (NumberFormatException ex) { throw invalid("categoryId", categoryValue, "categoryId must be a positive integer"); }
            if (categoryId < 1) throw invalid("categoryId", categoryValue, "categoryId must be a positive integer");
        }
        int page = parseInteger("page", pageValue);
        int size = parseInteger("size", sizeValue);

        if (page < 0) {
            throw invalid("page", pageValue, "page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("size", sizeValue, "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Sort sort = parseSort(sortValue);
        String normalizedSearchTerm = q == null || q.isBlank() ? null : q.trim();
        return new ProductQuery(normalizedSearchTerm, categoryId, PageRequest.of(page, size, sort));
    }

    public boolean hasSearchTerm() {
        return searchTerm != null;
    }

    private static int parseInteger(String parameter, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(parameter, value, parameter + " must be an integer");
        }
    }

    private static Sort parseSort(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length < 1 || parts.length > 2 || parts[0].isBlank()) {
            throw invalid("sort", value, "sort must use the format field,direction");
        }

        String field = parts[0].trim().toLowerCase(Locale.ROOT);
        if (!SORTABLE_FIELDS.contains(field)) {
            throw invalid("sort", value, "sort field must be name or price");
        }

        String directionValue = parts.length == 1 ? "asc" : parts[1].trim().toLowerCase(Locale.ROOT);
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(directionValue);
        } catch (IllegalArgumentException exception) {
            throw invalid("sort", value, "sort direction must be asc or desc");
        }

        return Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static InvalidProductQueryException invalid(String parameter, String value, String message) {
        return new InvalidProductQueryException(parameter, value, message);
    }
}
