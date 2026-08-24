package com.company.inventory.backup;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Drives the automatic daily backup. Runs every minute and lets
 * {@link BackupService#runAutomaticIfDue()} decide from settings whether a
 * backup is actually due - keeps the schedule configurable at runtime.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class AutomaticBackupScheduler {

    private final BackupService backupService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void tick() {
        backupService.runAutomaticIfDue();
    }
}
