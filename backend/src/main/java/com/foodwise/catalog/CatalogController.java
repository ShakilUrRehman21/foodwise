package com.foodwise.catalog;

import com.foodwise.common.ApiException;
import com.foodwise.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Shared (non-tenant) catalog endpoints; still require authentication. */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/search")
    public ApiResponse<List<CatalogItemDto>> search(@RequestParam(defaultValue = "") String q,
                                                    @RequestParam(defaultValue = "10") int limit) {
        if (q.length() > 100) {
            throw ApiException.validation("q", "Search text is too long.");
        }
        return ApiResponse.ok(catalogService.search(q, limit));
    }

    @GetMapping("/items/{id}")
    public ApiResponse<CatalogItemDto> item(@PathVariable UUID id) {
        return ApiResponse.ok(catalogService.item(id).orElseThrow(() -> ApiException.notFound("Catalog item not found.")));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryDto>> categories() {
        return ApiResponse.ok(catalogService.categories());
    }
}
