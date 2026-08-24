package com.company.inventory.backup;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.common.error.ApiException;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.security.AuthenticatedUserAccessor;
import com.company.inventory.settings.SettingsService;
import com.company.inventory.startup.AppPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Backup subsystem:
 *  - create: VACUUM INTO a clean snapshot, integrity-check it, zip with metadata.json
 *  - verify: re-open the zipped database and run PRAGMA integrity_check
 *  - retention: keep the newest N successful backups per type
 *  - import/export/restore (restore suspends the pool, swaps files, resumes)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BackupService {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String DB_ENTRY = "inventory.db";
    private static final String META_ENTRY = "metadata.json";

    private final BackupRecordRepository repository;
    private final SettingsService settingsService;
    private final AppPaths paths;
    private final ObjectMapper objectMapper;
    private final HikariDataSource dataSource;
    private final AuthenticatedUserAccessor accessor;
    private final com.company.inventory.audit.AuditService auditService;

    public record BackupDto(Long id, String filename, String backupType, String status,
                            long sizeBytes, boolean verified, String note,
                            String createdByName, LocalDateTime createdAt,
                            boolean existsOnDisk) {
    }

    private BackupDto toDto(BackupRecord b) {
        return new BackupDto(b.getId(), b.getFilename(), b.getBackupType().name(),
                b.getStatus().name(), b.getSizeBytes(), b.isVerified(), b.getNote(),
                b.getCreatedByName(), b.getCreatedAt(),
                Files.isRegularFile(fileOf(b)));
    }

    public record Metadata(String appVersion, String createdAt, String schemaVersion,
                           Map<String, Long> counts, String note) {
    }

    // ---------------------------------------------------------------- create

    public BackupDto create(BackupRecord.BackupType type, String note) {
        try {
            return createInternal(type, note);
        } catch (IOException e) {
            throw new ApiException(500, "BACKUP_IO_ERROR",
                    "Backup failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private BackupDto createInternal(BackupRecord.BackupType type, String note)
            throws IOException {
        Path targetDir = switch (type) {
            case AUTOMATIC -> paths.automaticBackups();
            case MANUAL -> paths.manualBackups();
            case SAFETY -> paths.safetyBackups();
            case IMPORTED -> paths.manualBackups();
        };
        Files.createDirectories(targetDir);

        String filename = "inventory-backup-"
                + LocalDateTime.now().format(STAMP) + "-"
                + Long.toString(System.nanoTime() & 0xFFFF, 36) + "-"
                + type.name().toLowerCase(Locale.ROOT) + ".zip";
        Path zipPath = targetDir.resolve(filename);

        Path tempDb = paths.temp().resolve("backup-" + System.nanoTime() + ".db");
        Files.createDirectories(paths.temp());
        cleanupSidecars(tempDb);

        // VACUUM INTO must not run inside any Spring-managed transaction.
        vacuumInto(tempDb);
        requireIntegrityOk(sqliteUrl(tempDb));

        Metadata meta = buildMetadata(note == null ? "" : note);
        writeZip(zipPath, tempDb, meta);
        long size = Files.size(zipPath);
        Files.deleteIfExists(tempDb);
        cleanupSidecars(tempDb);

        BackupRecord record = new BackupRecord();
        record.setFilename(filename);
        record.setBackupType(type);
        record.setStatus(BackupRecord.Status.SUCCESS);
        record.setSizeBytes(size);
        record.setVerified(true); // we just ran integrity_check on this exact snapshot
        record.setNote(meta.note());
        record.setCreatedBy(accessor.userId());
        record.setCreatedByName(accessor.username());
        record.setCreatedAt(LocalDateTime.now());
        repository.save(record);

        auditService.log(AuditActions.BACKUP_CREATE, "backup", record.getId(),
                "Created " + type + " backup " + filename);
        applyRetention();
        return toDto(record);
    }

    private void vacuumInto(Path tempDb) {
        String escaped = tempDb.toString().replace('\\', '/').replace("'", "''");
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            conn.setAutoCommit(true);
            st.execute("VACUUM INTO '" + escaped + "'");
        } catch (Exception e) {
            throw new ApiException(500, "BACKUP_VACUUM_FAILED",
                    "Could not snapshot database: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- verify

    public void verify(Long id) {
        BackupRecord record = find(id);
        Path zip = fileOf(record);
        if (!Files.isRegularFile(zip)) {
            throw ApiException.notFound("Backup file missing on disk: " + zip);
        }
        Path extracted = paths.temp().resolve("verify-" + System.nanoTime() + ".db");
        try {
            extractEntry(zip, DB_ENTRY, extracted);
            requireIntegrityOk(sqliteUrl(extracted));
        } catch (Exception e) {
            record.setStatus(BackupRecord.Status.FAILED);
            record.setErrorMessage("Verification failed: " + e.getMessage());
            repository.save(record);
            throw new ApiException(422, "BACKUP_CORRUPT",
                    "Backup failed verification: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(extracted);
            } catch (IOException ignored) {
            }
        }
        record.setVerified(true);
        record.setStatus(BackupRecord.Status.SUCCESS);
        repository.save(record);
    }

    private void requireIntegrityOk(String url) {
        try (Connection conn = java.sql.DriverManager.getConnection(url);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
            String result = rs.next() ? rs.getString(1) : "";
            if (!"ok".equalsIgnoreCase(result)) {
                throw new BusinessRuleException("INTEGRITY_CHECK_FAILED", "integrity_check returned: " + result);
            }
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("SNAPSHOT_UNREADABLE", "Could not open snapshot: " + e.getMessage());
        }
    }

    private String sqliteUrl(Path db) {
        return "jdbc:sqlite:" + db.toString().replace('\\', '/');
    }

    // ---------------------------------------------------------------- list/delete/export

    public List<BackupDto> list() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(this::toDto).toList();
    }

    public void delete(Long id) {
        BackupRecord record = find(id);
        try {
            Files.deleteIfExists(fileOf(record));
        } catch (IOException e) {
            throw new ApiException(500, "BACKUP_DELETE_FAILED",
                    "Could not delete file: " + e.getMessage());
        }
        repository.delete(record);
        auditService.log(AuditActions.BACKUP_DELETE, "backup", id,
                "Deleted backup " + record.getFilename());
    }

    public Path export(Long id, String targetDir) {
        BackupRecord record = find(id);
        if (targetDir == null || targetDir.isBlank()) {
            throw new ApiException(422, "INVALID_TARGET_DIR", "Target directory is required.");
        }
        Path source = fileOf(record);
        if (!Files.isRegularFile(source)) {
            throw ApiException.notFound("Backup file missing on disk.");
        }
        Path dir = Path.of(targetDir.trim());
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(record.getFilename());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            auditService.log(AuditActions.BACKUP_EXPORT, "backup", id,
                    "Exported " + record.getFilename() + " to " + target);
            return target;
        } catch (IOException e) {
            throw new ApiException(500, "BACKUP_EXPORT_FAILED",
                    "Could not copy backup: " + e.getMessage());
        }
    }

    public BackupDto importFile(String sourcePath, String note) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new ApiException(422, "INVALID_SOURCE", "Source path is required.");
        }
        Path src = Path.of(sourcePath.trim());
        if (!Files.isRegularFile(src)) {
            throw ApiException.notFound("Source file not found: " + src);
        }
        Path target = paths.manualBackups()
                .resolve("imported-" + LocalDateTime.now().format(STAMP) + "-"
                        + Long.toString(System.nanoTime() & 0xFFFF, 36) + ".zip");
        try {
            Files.createDirectories(target.getParent());
            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(500, "BACKUP_IMPORT_FAILED",
                    "Could not copy file: " + e.getMessage());
        }
        BackupRecord record = new BackupRecord();
        record.setFilename(target.getFileName().toString());
        record.setBackupType(BackupRecord.BackupType.IMPORTED);
        record.setStatus(BackupRecord.Status.SUCCESS);
        try {
            record.setSizeBytes(Files.size(target));
        } catch (IOException e) {
            record.setSizeBytes(0);
        }
        record.setNote(note == null ? "" : note);
        record.setCreatedByName(accessor.username());
        record.setCreatedAt(LocalDateTime.now());

        // verify before accepting
        Path extracted = paths.temp().resolve("import-" + System.nanoTime() + ".db");
        try {
            extractEntry(target, DB_ENTRY, extracted);
            requireIntegrityOk(sqliteUrl(extracted));
            record.setVerified(true);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
            throw new ApiException(422, "BACKUP_CORRUPT",
                    "Imported file is not a valid backup: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(extracted);
            } catch (IOException ignored) {
            }
        }
        repository.save(record);
        auditService.log(AuditActions.BACKUP_IMPORT, "backup", record.getId(),
                "Imported backup " + record.getFilename());
        return toDto(record);
    }

    // ---------------------------------------------------------------- restore

    public void restore(Long id) {
        BackupRecord record = find(id);
        Path zip = fileOf(record);
        if (!Files.isRegularFile(zip)) {
            throw ApiException.notFound("Backup file missing on disk.");
        }
        if (!record.isVerified()) {
            verify(id); // refuse to restore anything that does not pass verification
        }

        Metadata meta;
        try {
            meta = readMetadata(zip);
        } catch (Exception e) {
            throw new ApiException(422, "BACKUP_CORRUPT",
                    "Cannot read backup metadata: " + e.getMessage());
        }
        String currentSchema = liveSchemaVersion();
        if (meta.schemaVersion() != null && !meta.schemaVersion().equals(currentSchema)) {
            throw new ApiException(422, "SCHEMA_VERSION_MISMATCH",
                    "Backup schema (" + meta.schemaVersion()
                            + ") does not match application schema (" + currentSchema + ").");
        }

        // safety snapshot of the CURRENT state first - always recoverable
        try {
            createInternal(BackupRecord.BackupType.SAFETY,
                    "Automatic safety backup before restoring " + record.getFilename());
        } catch (IOException e) {
            throw new ApiException(500, "BACKUP_IO_ERROR",
                    "Safety backup before restore failed: " + e.getMessage());
        }

        Path extracted = paths.temp().resolve("restore-" + System.nanoTime() + ".db");
        try {
            extractEntry(zip, DB_ENTRY, extracted);
            requireIntegrityOk(sqliteUrl(extracted));
        } catch (Exception e) {
            try {
                Files.deleteIfExists(extracted);
            } catch (IOException ignored) {
            }
            throw new ApiException(422, "BACKUP_CORRUPT",
                    "Backup content failed verification: " + e.getMessage());
        }

        swapDatabaseFile(extracted);
        auditService.log(AuditActions.BACKUP_RESTORE, "backup", id,
                "Restored database from " + record.getFilename());
    }

    /**
     * Suspends the Hikari pool so no NEW connections are handed out, waits for
     * in-flight work to finish, evicts idle connections, then swaps the file.
     * New connections opened after resume() see the restored database.
     */
    private void swapDatabaseFile(Path newDb) {
        var pool = dataSource.getHikariPoolMXBean();
        if (pool == null) {
            throw new ApiException(500, "RESTORE_POOL_ERROR", "Pool unavailable for restore.");
        }
        pool.suspendPool();
        try {
            long deadline = System.currentTimeMillis() + 15_000;
            while (pool.getActiveConnections() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            if (pool.getActiveConnections() > 0) {
                throw new ApiException(422, "RESTORE_BUSY",
                        "System is busy - active operations did not finish. Try again.");
            }
            for (int i = 0; i < 5 && pool.getTotalConnections() > 0; i++) {
                pool.softEvictConnections();
                Thread.sleep(150);
            }
            if (pool.getActiveConnections() > 0 || pool.getTotalConnections() > 0) {
                throw new ApiException(422, "RESTORE_BUSY",
                        "Could not drain connection pool. Try again when idle.");
            }

            Path liveDb = paths.databaseFile();
            cleanupSidecars(liveDb);
            Files.copy(newDb, liveDb, StandardCopyOption.REPLACE_EXISTING);
            cleanupSidecars(liveDb);
            log.info("Database restored from backup; file swapped at {}", liveDb);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "RESTORE_INTERRUPTED", "Restore was interrupted.");
        } catch (IOException e) {
            throw new ApiException(500, "RESTORE_IO_ERROR",
                    "Could not replace database file: " + e.getMessage());
        } finally {
            pool.resumePool();
        }
    }

    // ---------------------------------------------------------------- scheduler support

    public void runAutomaticIfDue() {
        boolean enabled = settingsService.getBool("backup.enabled", true);
        if (!enabled) {
            return;
        }
        String time = settingsService.get("backup.time");
        String hhmm = time == null || time.isBlank() ? "02:00" : time.trim();
        LocalDateTime now = LocalDateTime.now();
        if (!now.format(DateTimeFormatter.ofPattern("HH:mm")).equals(hhmm)) {
            return;
        }
        var lastAuto = repository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(b -> b.getBackupType() == BackupRecord.BackupType.AUTOMATIC)
                .filter(b -> b.getStatus() == BackupRecord.Status.SUCCESS)
                .findFirst();
        if (lastAuto.isPresent()
                && lastAuto.get().getCreatedAt().toLocalDate().equals(now.toLocalDate())) {
            return; // already backed up today
        }
        try {
            createInternal(BackupRecord.BackupType.AUTOMATIC, "Scheduled daily backup");
        } catch (Exception e) {
            log.error("Automatic backup failed", e);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void applyRetention() {
        int keep = settingsService.getInt("backup.retention.count", 30);
        if (keep < 1) {
            keep = 1;
        }
        for (BackupRecord.BackupType type : BackupRecord.BackupType.values()) {
            List<BackupRecord> success = repository.findAll(
                            Sort.by(Sort.Direction.DESC, "id")).stream()
                    .filter(b -> b.getBackupType() == type)
                    .filter(b -> b.getStatus() == BackupRecord.Status.SUCCESS)
                    .toList();
            for (int i = keep; i < success.size(); i++) {
                BackupRecord old = success.get(i);
                try {
                    Files.deleteIfExists(fileOf(old));
                } catch (IOException e) {
                    log.warn("Could not delete retained-out backup file {}", old.getFilename(), e);
                }
                repository.delete(old);
            }
        }
    }

    private Metadata buildMetadata(String note) throws IOException {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("products", countTable("products"));
        counts.put("stock_movements", countTable("stock_movements"));
        counts.put("purchases", countTable("purchases"));
        counts.put("issues", countTable("issues"));
        counts.put("users", countTable("users"));
        return new Metadata(appVersion(), LocalDateTime.now()
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                liveSchemaVersion(), counts, note);
    }

    private long countTable(String table) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    private String liveSchemaVersion() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String appVersion() {
        return "1.0.0";
    }

    private void writeZip(Path zipPath, Path dbFile, Metadata meta) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                new java.io.BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            zos.putNextEntry(new ZipEntry(DB_ENTRY));
            try (InputStream in = Files.newInputStream(dbFile)) {
                in.transferTo(zos);
            }
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(META_ENTRY));
            zos.write(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(meta));
            zos.closeEntry();
        }
    }

    public static Path fileOf(BackupRecord b, AppPaths paths) {
        Path base = switch (b.getBackupType()) {
            case AUTOMATIC -> paths.automaticBackups();
            case SAFETY -> paths.safetyBackups();
            default -> paths.manualBackups();
        };
        return base.resolve(b.getFilename());
    }

    private Path fileOf(BackupRecord b) {
        return fileOf(b, paths);
    }

    private void extractEntry(Path zip, String entryName, Path target) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entry = zf.getEntry(entryName);
            if (entry == null) {
                throw new IOException("missing entry " + entryName);
            }
            try (InputStream in = zf.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public Metadata readMetadata(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entry = zf.getEntry(META_ENTRY);
            if (entry == null) {
                return new Metadata(null, null, null, Map.of(), "");
            }
            try (InputStream in = zf.getInputStream(entry)) {
                return objectMapper.readValue(in, Metadata.class);
            }
        }
    }

    private void cleanupSidecars(Path dbFile) {
        try {
            Files.deleteIfExists(dbFile.resolveSibling(dbFile.getFileName() + "-wal"));
            Files.deleteIfExists(dbFile.resolveSibling(dbFile.getFileName() + "-shm"));
        } catch (IOException ignored) {
        }
    }

    private BackupRecord find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Backup not found: " + id));
    }

    public Path filePathOf(Long id) {
        return fileOf(find(id));
    }
}
