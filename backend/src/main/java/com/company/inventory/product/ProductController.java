package com.company.inventory.product;

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
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public PageResponse<ProductDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ProductDto> result = productService.search(new ProductService.ProductSearchCriteria(
                search, categoryId, unitId, supplierId, active, stockStatus, page, size));
        return PageResponse.of(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ProductDto get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductRequest request) {
        ProductDto created = productService.create(request);
        auditService.log(AuditActions.PRODUCT_CREATE, "PRODUCT", created.id(),
                "Created product '" + created.name() + "'");
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ResponseEntity<ProductDto> update(@PathVariable Long id,
                                             @Valid @RequestBody ProductRequest request) {
        ProductDto updated = productService.update(id, request);
        auditService.log(AuditActions.PRODUCT_UPDATE, "PRODUCT", id,
                "Updated product '" + updated.name() + "'");
        return ResponseEntity.ok(updated);
    }

    /** Deactivates the product (soft-delete, hides from dropdowns). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        ProductDto updated = productService.setActive(id, false);
        auditService.log(AuditActions.PRODUCT_DEACTIVATE, "PRODUCT", id,
                "Deactivated product '" + updated.name() + "'");
        return ResponseEntity.ok(java.util.Map.of("message", "Product deactivated."));
    }

    /** Permanently deletes the product — only allowed when no history exists. */
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ResponseEntity<?> deletePermanently(@PathVariable Long id) {
        productService.deletePermanently(id);
        auditService.log(AuditActions.PRODUCT_DEACTIVATE, "PRODUCT", id,
                "Permanently deleted product #" + id);
        return ResponseEntity.ok(java.util.Map.of("message", "Product permanently deleted."));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ResponseEntity<?> activate(@PathVariable Long id) {
        ProductDto updated = productService.setActive(id, true);
        auditService.log(AuditActions.PRODUCT_UPDATE, "PRODUCT", id,
                "Reactivated product '" + updated.name() + "'");
        return ResponseEntity.ok(updated);
    }
}
