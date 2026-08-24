package com.company.inventory.report;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.security.AuthenticatedUserAccessor;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportFileWriter fileWriter;
    private final AuditService auditService;
    private final AuthenticatedUserAccessor accessor;

    public record ReportRequest(ReportService.Format format, java.time.LocalDate dateFrom,
                                java.time.LocalDate dateTo, Long productId, Long categoryId,
                                Long supplierId, String username) {

        ReportService.ReportParams params() {
            return new ReportService.ReportParams(dateFrom(), dateTo(), productId(),
                    categoryId(), supplierId(), username());
        }
    }

    @PostMapping("/{type}")
    @PreAuthorize("hasAuthority('REPORT_VIEW') || hasAuthority('REPORT_EXPORT')")
    public Object generate(@PathVariable String type, @RequestBody ReportRequest req) {
        ReportService.Format format = req.format() == null
                ? ReportService.Format.JSON : req.format();
        ReportService.ReportTable table = reportService.build(type, req.params());

        if (format == ReportService.Format.JSON) {
            return table;
        }
        if (!accessor.currentUser().hasPermission("REPORT_EXPORT")) {
            throw new com.company.inventory.common.error.ApiException(403, "FORBIDDEN",
                    "REPORT_EXPORT permission required to export files.");
        }
        var file = fileWriter.write(type, format, table);
        auditService.log(AuditActions.REPORT_EXPORT,
                "report", null,
                "Exported " + type + " as " + format + " -> " + file.fileName());
        return file;
    }

    @GetMapping("/files/{fileName:.+}")
    @PreAuthorize("hasAuthority('REPORT_VIEW') || hasAuthority('REPORT_EXPORT')")
    public StreamingResponseBody download(@PathVariable String fileName,
                                          HttpServletResponse response) throws IOException {
        Path path = fileWriter.resolveDownload(fileName);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentLengthLong(Files.size(path));
        return out -> {
            try (InputStream in = Files.newInputStream(path)) {
                in.transferTo(out);
            }
        };
    }
}
