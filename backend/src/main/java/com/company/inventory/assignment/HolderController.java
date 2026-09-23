package com.company.inventory.assignment;

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

import java.util.List;

@RestController
@RequestMapping("/api/holders")
@RequiredArgsConstructor
public class HolderController {

    private final HolderService holderService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('HOLDER_VIEW')")
    public PageResponse<HolderService.HolderDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<HolderService.HolderDto> result = holderService.search(search, type, active, page, size);
        return PageResponse.of(result);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('HOLDER_VIEW')")
    public List<HolderService.HolderDto> all(
            @RequestParam(required = false) Boolean active) {
        return holderService.all(active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('HOLDER_VIEW')")
    public HolderService.HolderDto get(@PathVariable Long id) {
        return holderService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('HOLDER_MANAGE')")
    public ResponseEntity<HolderService.HolderDto> create(
            @Valid @RequestBody HolderService.HolderRequest request) {
        var created = holderService.create(request);
        auditService.log(AuditActions.HOLDER_CREATE, "HOLDER", created.id(),
                "Created holder '" + created.name() + "' (" + created.type() + ")");
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('HOLDER_MANAGE')")
    public ResponseEntity<HolderService.HolderDto> update(
            @PathVariable Long id, @Valid @RequestBody HolderService.HolderRequest request) {
        var updated = holderService.update(id, request);
        auditService.log(AuditActions.HOLDER_UPDATE, "HOLDER", id,
                "Updated holder '" + updated.name() + "'");
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('HOLDER_MANAGE')")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        holderService.deactivate(id);
        auditService.log(AuditActions.HOLDER_DELETE, "HOLDER", id,
                "Deactivated holder id=" + id);
        return ResponseEntity.ok(java.util.Map.of("message", "Holder deactivated."));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('HOLDER_MANAGE')")
    public ResponseEntity<?> activate(@PathVariable Long id) {
        holderService.activate(id);
        auditService.log(AuditActions.HOLDER_UPDATE, "HOLDER", id,
                "Activated holder id=" + id);
        return ResponseEntity.ok(java.util.Map.of("message", "Holder activated."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('HOLDER_MANAGE')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var removed = holderService.get(id);
        holderService.delete(id);
        auditService.log(AuditActions.HOLDER_DELETE, "HOLDER", id,
                "Deleted holder '" + removed.name() + "'");
        return ResponseEntity.ok(java.util.Map.of("message", "Holder deleted."));
    }
}