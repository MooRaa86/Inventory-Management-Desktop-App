package com.company.inventory.backup;

import com.company.inventory.common.jpa.IsoDateTimeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "backups")
public class BackupRecord {

    public enum BackupType {AUTOMATIC, MANUAL, SAFETY, IMPORTED}

    public enum Status {SUCCESS, FAILED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(name = "backup_type", nullable = false)
    private BackupType backupType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes = 0L;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(nullable = false)
    private String note = "";

    @Column(name = "error_message", nullable = false)
    private String errorMessage = "";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", nullable = false)
    private String createdByName = "system";

    @Convert(converter = IsoDateTimeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
