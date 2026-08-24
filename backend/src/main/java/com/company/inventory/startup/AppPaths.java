package com.company.inventory.startup;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class AppPaths {

    private final Path root;
    private final String dataDir;
    private final String filesDir;
    private final String backupsDir;
    private final String automaticBackupsDir;
    private final String manualBackupsDir;
    private final String safetyBackupsDir;
    private final String tempDir;
    private final String configDir;
    private final String logsDir;
    private final String exportsDir;
    private final String reportsExportDir;
    private final String backupsExportDir;
    private final String documentsDir;

    public AppPaths(@Value("${app.root:.}") String root,
                    @Value("${app.dir.data:data}") String dataDir,
                    @Value("${app.dir.files:data/files}") String filesDir,
                    @Value("${app.dir.backups:backups}") String backupsDir,
                    @Value("${app.dir.backups-automatic:backups/automatic}") String automaticBackupsDir,
                    @Value("${app.dir.backups-manual:backups/manual}") String manualBackupsDir,
                    @Value("${app.dir.backups-safety:backups/safety}") String safetyBackupsDir,
                    @Value("${app.dir.temp:data/tmp}") String tempDir,
                    @Value("${app.dir.config:config}") String configDir,
                    @Value("${app.logs-dir:logs}") String logsDir,
                    @Value("${app.dir.exports:exports}") String exportsDir,
                    @Value("${app.dir.exports-reports:exports/reports}") String reportsExportDir,
                    @Value("${app.dir.exports-backups:exports/backups}") String backupsExportDir,
                    @Value("${app.dir.documents:documents}") String documentsDir) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.dataDir = dataDir;
        this.filesDir = filesDir;
        this.backupsDir = backupsDir;
        this.automaticBackupsDir = automaticBackupsDir;
        this.manualBackupsDir = manualBackupsDir;
        this.safetyBackupsDir = safetyBackupsDir;
        this.tempDir = tempDir;
        this.configDir = configDir;
        this.logsDir = logsDir;
        this.exportsDir = exportsDir;
        this.reportsExportDir = reportsExportDir;
        this.backupsExportDir = backupsExportDir;
        this.documentsDir = documentsDir;
    }

    public static AppPaths forTesting(Path root) {
        return new AppPaths(root.toString(), "data", "data/files", "backups",
                "backups/automatic", "backups/manual", "backups/safety", "data/tmp",
                "config", "logs", "exports", "exports/reports", "exports/backups", "documents");
    }

    @PostConstruct
    void logRoot() {
        log.info("Application root resolved to: {}", root);
    }

    public Path root() {
        return root;
    }

    public Path resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("Relative path must not be blank");
        }
        Path p = Paths.get(relative);
        if (p.isAbsolute()) {
            throw new IllegalArgumentException("Absolute paths are not allowed: " + relative);
        }
        return root.resolve(relative).normalize();
    }

    public Path data() {
        return resolve(dataDir);
    }

    public Path databaseFile() {
        return resolve("data/inventory.db");
    }

    public Path files() {
        return resolve(filesDir);
    }

    public Path backups() {
        return resolve(backupsDir);
    }

    public Path automaticBackups() {
        return resolve(automaticBackupsDir);
    }

    public Path manualBackups() {
        return resolve(manualBackupsDir);
    }

    public Path safetyBackups() {
        return resolve(safetyBackupsDir);
    }

    public Path temp() {
        return resolve(tempDir);
    }

    public Path config() {
        return resolve(configDir);
    }

    public Path logs() {
        return resolve(logsDir);
    }

    public Path exports() {
        return resolve(exportsDir);
    }

    public Path reportsExports() {
        return resolve(reportsExportDir);
    }

    public Path backupsExports() {
        return resolve(backupsExportDir);
    }

    public Path documents() {
        return resolve(documentsDir);
    }

    public Path ensure(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create required directory: " + dir, e);
        }
    }

    public void ensureAll() {
        ensure(data());
        ensure(files());
        ensure(backups());
        ensure(automaticBackups());
        ensure(manualBackups());
        ensure(safetyBackups());
        ensure(temp());
        ensure(config());
        ensure(logs());
        ensure(exports());
        ensure(reportsExports());
        ensure(backupsExports());
        ensure(documents());
    }

    public Path ensureParents(Path file) {
        Path parent = file.getParent();
        if (parent != null) {
            ensure(parent);
        }
        return file;
    }
}
