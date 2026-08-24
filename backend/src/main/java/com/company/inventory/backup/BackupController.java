package com.company.inventory.backup;

import com.company.inventory.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    public record CreateRequest(String note) {
    }

    public record ExportRequest(String targetDir) {
    }

    public record ImportRequest(String sourcePath, String note) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BACKUP_VIEW')")
    public List<BackupService.BackupDto> list() {
        return backupService.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BACKUP_CREATE')")
    public BackupService.BackupDto create(@RequestBody(required = false) CreateRequest req) {
        return backupService.create(BackupRecord.BackupType.MANUAL,
                req == null ? "" : req.note());
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('BACKUP_CREATE')")
    public void verify(@PathVariable Long id) {
        backupService.verify(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('BACKUP_DELETE')")
    public void delete(@PathVariable Long id) {
        backupService.delete(id);
    }

    @PostMapping("/{id}/export")
    @PreAuthorize("hasAuthority('BACKUP_EXPORT')")
    public java.util.Map<String, String> export(@PathVariable Long id,
                                                @RequestBody ExportRequest req) {
        Path target = backupService.export(id, req.targetDir());
        return java.util.Map.of("savedTo", target.toString(),
                "fileName", target.getFileName().toString());
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('BACKUP_IMPORT')")
    public BackupService.BackupDto importBackup(@RequestBody ImportRequest req) {
        return backupService.importFile(req.sourcePath(), req.note());
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('BACKUP_RESTORE')")
    public java.util.Map<String, String> restore(@PathVariable Long id) {
        backupService.restore(id);
        return java.util.Map.of("status", "RESTORED",
                "message", "Database restored. Data reflects the selected backup.");
    }

    @GetMapping(value = "/{id}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAuthority('BACKUP_VIEW')")
    public StreamingResponseBody download(@PathVariable Long id,
                                          jakarta.servlet.http.HttpServletResponse response)
            throws IOException {
        Path file = backupService.filePathOf(id);
        if (!Files.isRegularFile(file)) {
            throw ApiException.notFound("Backup file missing on disk.");
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + file.getFileName() + "\"");
        response.setContentLengthLong(Files.size(file));
        return out -> {
            try (InputStream in = Files.newInputStream(file)) {
                in.transferTo(out);
            }
        };
    }
}
