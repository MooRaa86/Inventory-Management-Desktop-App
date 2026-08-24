package com.company.inventory.backup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Lightweight backup statistics used by the dashboard and the backup screen.
 */
@Service
@RequiredArgsConstructor
public class BackupStatsService {

    private final BackupRecordRepository repository;

    public record BackupStats(LocalDateTime lastSuccessfulBackupAt, long successfulCount,
                              long failedCount, boolean overdue, long automaticCount) {
    }

    @Transactional(readOnly = true)
    public BackupStats getStats() {
        LocalDateTime last = repository.findLastSuccessfulAt();
        // "overdue" = backups exist, but the newest successful one is >= 24h old.
        boolean overdue = last != null
                && Duration.between(last, LocalDateTime.now()).toHours() >= 24;
        return new BackupStats(last,
                repository.countByStatus(BackupRecord.Status.SUCCESS),
                repository.countByStatus(BackupRecord.Status.FAILED),
                overdue,
                repository.countByStatusAndBackupType(BackupRecord.Status.SUCCESS,
                        BackupRecord.BackupType.AUTOMATIC));
    }
}
