package com.company.inventory.audit;

import com.company.inventory.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public AuditService(AuditLogRepository auditLogRepository,
                        ObjectMapper objectMapper,
                        PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        // REQUIRED (not REQUIRES_NEW): joining the caller's transaction avoids a
        // second SQLite connection contending for the single writer lock while
        // the outer transaction is open - that caused SQLITE_BUSY deadlocks.
        // Callers without an active transaction (e.g. failed login) still get
        // their own transaction automatically.
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);
    }

    public void log(String action, String entityType, Object entityId, String description) {
        log(action, entityType, entityId, description, Map.of());
    }

    public void log(String action, String entityType, Object entityId, String description,
                    Map<String, ?> metadata) {
        AuthenticatedUser principal = currentUser();
        Long userId = principal != null ? principal.id() : null;
        String username = principal != null ? principal.username() : "anonymous";
        try {
            txTemplate.executeWithoutResult(status ->
                    auditLogRepository.save(buildEntry(userId, username, action, entityType,
                            entityId, description, metadata)));
        } catch (Exception e) {
            log.error("Failed to persist audit log for action={}", action, e);
        }
    }

    private AuditLog buildEntry(Long userId, String username, String action, String entityType,
                                Object entityId, String description, Map<String, ?> metadata) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setUsername(username == null ? "" : username);
        entry.setAction(action);
        entry.setEntityType(entityType == null ? "" : entityType);
        entry.setEntityId(entityId == null ? "" : String.valueOf(entityId));
        entry.setDescription(description == null ? "" : description);
        entry.setMetadata(toJson(metadata));
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private AuthenticatedUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
