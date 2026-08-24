package com.company.inventory.category;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.common.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('CATEGORY_VIEW')")
    public PageResponse<CategoryDto> list(@RequestParam(required = false) String search,
                                          @RequestParam(required = false) Boolean active,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Page<CategoryDto> result = categoryService.search(search, active, page, Math.min(size, 200));
        return PageResponse.of(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_VIEW')")
    public CategoryDto get(@PathVariable Long id) {
        return categoryService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORY_CREATE')")
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody CategoryRequest request) {
        CategoryDto created = categoryService.create(request);
        auditService.log(AuditActions.CATEGORY_CREATE, "CATEGORY", created.id(),
                "Created category '" + created.name() + "'");
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_UPDATE')")
    public ResponseEntity<CategoryDto> update(@PathVariable Long id,
                                              @Valid @RequestBody CategoryRequest request) {
        CategoryDto updated = categoryService.update(id, request);
        auditService.log(AuditActions.CATEGORY_UPDATE, "CATEGORY", id,
                "Updated category '" + updated.name() + "'");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_DELETE')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        CategoryDto removed = categoryService.get(id);
        categoryService.delete(id);
        auditService.log(AuditActions.CATEGORY_DELETE, "CATEGORY", id,
                "Deleted category '" + removed.name() + "'");
        return ResponseEntity.ok(java.util.Map.of("message", "Category deleted."));
    }
}
