package com.company.inventory.assignment;

import com.company.inventory.common.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public PageResponse<AssignmentService.AssignmentDto> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long holderId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AssignmentService.AssignmentDto> result =
                assignmentService.search(productId, holderId, search, page, size);
        return PageResponse.of(result);
    }

    @GetMapping("/holder")
    @PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public List<AssignmentService.AssignmentDto> byHolder(@RequestParam Long holderId) {
        return assignmentService.listByHolder(holderId);
    }

    @GetMapping("/product")
    @PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public List<AssignmentService.AssignmentDto> byProduct(@RequestParam Long productId) {
        return assignmentService.listByProduct(productId);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public PageResponse<AssignmentService.TransferDto> history(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long holderId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AssignmentService.TransferDto> result =
                assignmentService.history(productId, holderId, search, page, size);
        return PageResponse.of(result);
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('ASSIGNMENT_MANAGE')")
    public ResponseEntity<AssignmentService.AssignmentDto> assign(
            @Valid @RequestBody AssignmentService.AssignRequest request) {
        return ResponseEntity.ok(assignmentService.assign(request));
    }

    @PostMapping("/unassign")
    @PreAuthorize("hasAuthority('ASSIGNMENT_MANAGE')")
    public ResponseEntity<AssignmentService.AssignmentDto> unassign(
            @Valid @RequestBody AssignmentService.UnassignRequest request) {
        return ResponseEntity.ok(assignmentService.unassign(request));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('ASSIGNMENT_MANAGE')")
    public ResponseEntity<AssignmentService.AssignmentDto> transfer(
            @Valid @RequestBody AssignmentService.TransferRequest request) {
        return ResponseEntity.ok(assignmentService.transfer(request));
    }
}