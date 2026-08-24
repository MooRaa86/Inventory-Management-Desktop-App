package com.company.inventory.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String username = "";

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType = "";

    @Column(name = "entity_id", nullable = false)
    private String entityId = "";

    @Column(nullable = false)
    private String description = "";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
