package com.company.inventory.issue;

import com.company.inventory.audit.AuditService;
import com.company.inventory.common.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('ISSUE_VIEW')")
    public PageResponse<IssueService.IssueDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Issue.Status status,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<IssueService.IssueDto> result = issueService.search(search, status, department, page, size);
        return PageResponse.of(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ISSUE_VIEW')")
    public IssueService.IssueDto get(@PathVariable Long id) {
        return issueService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ISSUE_CREATE')")
    public ResponseEntity<IssueService.IssueDto> create(
            @Valid @RequestBody IssueService.IssueCreateRequest request) {
        return ResponseEntity.ok(issueService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ISSUE_CREATE')")
    public ResponseEntity<IssueService.IssueDto> update(
            @PathVariable Long id, @Valid @RequestBody IssueService.IssueCreateRequest request) {
        return ResponseEntity.ok(issueService.update(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ISSUE_APPROVE')")
    public ResponseEntity<IssueService.IssueDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(issueService.approve(id));
    }

    /** Completing deducts stock for all lines atomically. */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('ISSUE_COMPLETE')")
    public ResponseEntity<IssueService.IssueDto> complete(@PathVariable Long id) {
        return ResponseEntity.ok(issueService.complete(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ISSUE_CANCEL')")
    public ResponseEntity<IssueService.IssueDto> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(issueService.cancel(id));
    }
}
