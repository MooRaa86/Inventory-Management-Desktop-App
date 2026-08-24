package com.company.inventory.backup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {

    @Query("SELECT MAX(b.createdAt) FROM BackupRecord b WHERE b.status = 'SUCCESS'")
    LocalDateTime findLastSuccessfulAt();

    long countByStatus(BackupRecord.Status status);

    long countByStatusAndBackupType(BackupRecord.Status status, BackupRecord.BackupType backupType);
}
