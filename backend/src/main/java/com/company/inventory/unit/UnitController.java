package com.company.inventory.unit;

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
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('UNIT_VIEW')")
    public PageResponse<UnitService.UnitDto> list(@RequestParam(required = false) String search,
                                                  @RequestParam(required = false) Boolean active,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        Page<UnitService.UnitDto> result = unitService.search(search, active, page, size);
        return PageResponse.of(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('UNIT_VIEW')")
    public UnitService.UnitDto get(@PathVariable Long id) {
        return unitService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('UNIT_CREATE')")
    public ResponseEntity<UnitService.UnitDto> create(@Valid @RequestBody UnitService.UnitRequest request) {
        UnitService.UnitDto created = unitService.create(request);
        auditService.log(AuditActions.UNIT_CREATE, "UNIT", created.id(),
                "Created unit '" + created.name() + "'");
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UNIT_UPDATE')")
    public ResponseEntity<UnitService.UnitDto> update(@PathVariable Long id,
                                                      @Valid @RequestBody UnitService.UnitRequest request) {
        UnitService.UnitDto updated = unitService.update(id, request);
        auditService.log(AuditActions.UNIT_UPDATE, "UNIT", id,
                "Updated unit '" + updated.name() + "'");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('UNIT_DELETE')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        UnitService.UnitDto removed = unitService.get(id);
        unitService.delete(id);
        auditService.log(AuditActions.UNIT_DELETE, "UNIT", id,
                "Deleted unit '" + removed.name() + "'");
        return ResponseEntity.ok(java.util.Map.of("message", "Unit deleted."));
    }
}
