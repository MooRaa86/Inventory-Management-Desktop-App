package com.company.inventory.supplier;

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
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    public PageResponse<SupplierService.SupplierDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SupplierService.SupplierDto> result = supplierService.search(search, active, page, size);
        return PageResponse.of(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    public SupplierService.SupplierDto get(@PathVariable Long id) {
        return supplierService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPPLIER_CREATE')")
    public ResponseEntity<SupplierService.SupplierDto> create(
            @Valid @RequestBody SupplierService.SupplierRequest request) {
        var created = supplierService.create(request);
        auditService.log(AuditActions.SUPPLIER_CREATE, "SUPPLIER", created.id(),
                "Created supplier '" + created.name() + "'");
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_UPDATE')")
    public ResponseEntity<SupplierService.SupplierDto> update(
            @PathVariable Long id, @Valid @RequestBody SupplierService.SupplierRequest request) {
        var updated = supplierService.update(id, request);
        auditService.log(AuditActions.SUPPLIER_UPDATE, "SUPPLIER", id,
                "Updated supplier '" + updated.name() + "'");
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('SUPPLIER_DELETE')")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        supplierService.deactivate(id);
        auditService.log(AuditActions.SUPPLIER_DELETE, "SUPPLIER", id,
                "Deactivated supplier id=" + id);
        return ResponseEntity.ok(java.util.Map.of("message", "Supplier deactivated."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_DELETE')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var removed = supplierService.get(id);
        supplierService.delete(id);
        auditService.log(AuditActions.SUPPLIER_DELETE, "SUPPLIER", id,
                "Deleted supplier '" + removed.name() + "'");
        return ResponseEntity.ok(java.util.Map.of("message", "Supplier deleted."));
    }
}
