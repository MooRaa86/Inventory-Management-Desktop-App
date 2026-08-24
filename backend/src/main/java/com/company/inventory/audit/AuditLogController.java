package com.company.inventory.audit;

import com.company.inventory.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public record AuditLogDto(Long id, Long userId, String username, String action,
                              String entityType, String entityId, String description,
                              String metadata, LocalDateTime createdAt) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<AuditLogDto> search(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<AuditLogDto> result = auditLogRepository.search(username, action, entityType, from, to,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                                Sort.by(Sort.Direction.DESC, "id")))
                .map(a -> new AuditLogDto(a.getId(), a.getUserId(), a.getUsername(), a.getAction(),
                        a.getEntityType(), a.getEntityId(), a.getDescription(),
                        a.getMetadata(), a.getCreatedAt()));
        return PageResponse.of(result);
    }
}
