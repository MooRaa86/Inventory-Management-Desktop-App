package com.company.inventory.purchase;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.common.error.ApiException;
import com.company.inventory.common.web.PageResponse;
import com.company.inventory.audit.AuditService;
import com.company.inventory.report.PdfReportRenderer;
import com.company.inventory.report.ReportFileWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final AuditService auditService;
    private final ReportFileWriter fileWriter;
    private final com.company.inventory.settings.SettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public PageResponse<PurchaseService.PurchaseDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Purchase.Status status,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PurchaseService.PurchaseDto> result = purchaseService.search(
                search, supplierId, status, dateFrom, dateTo, page, size);
        return PageResponse.of(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public PurchaseService.PurchaseDto get(@PathVariable Long id) {
        return purchaseService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public ResponseEntity<PurchaseService.PurchaseDto> create(
            @Valid @RequestBody PurchaseService.PurchaseCreateRequest request) {
        var created = purchaseService.create(request);
        return ResponseEntity.ok(created);
    }

    /** Receiving creates STOCK_IN movements for every line, atomically. */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('PURCHASE_RECEIVE')")
    public ResponseEntity<PurchaseService.PurchaseDto> receive(@PathVariable Long id) {
        var received = purchaseService.receive(id);
        return ResponseEntity.ok(received);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public ResponseEntity<PurchaseService.PurchaseDto> cancel(@PathVariable Long id) {
        var cancelled = purchaseService.cancel(id);
        auditService.log(AuditActions.PURCHASE_CANCEL, "PURCHASE", id,
                "Cancelled purchase " + cancelled.purchaseNumber());
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public StreamingResponseBody exportPdf(@PathVariable Long id,
                                            HttpServletResponse response) throws IOException {
        PurchaseService.PurchaseDto dto = purchaseService.get(id);
        String companyName = settingsService.get("company.name");
        if (companyName == null || companyName.isBlank()) companyName = settingsService.get("app.name");

        String fileName = "purchase_" + (dto.purchaseNumber() != null ? dto.purchaseNumber().replaceAll("[^a-zA-Z0-9]", "_") : id) + ".pdf";
        Path exportDir = fileWriter.getReportsDir();
        Files.createDirectories(exportDir);
        Path target = exportDir.resolve(fileName);

        try (var out = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
            java.math.BigDecimal total = dto.totalAmount();
            PdfReportRenderer.renderPurchaseInvoice(
                    out, companyName,
                    dto.purchaseNumber(), dto.supplierName(),
                    dto.purchaseDate() != null ? dto.purchaseDate().toString() : "",
                    dto.status().name(),
                    dto.createdByName(),
                    dto.receivedAt() != null
                            ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dto.receivedAt())
                            + " by " + (dto.receivedByName() != null ? dto.receivedByName() : "")
                            : null,
                    dto.notes(),
                    java.util.List.of("Product", "Qty", "Unit Cost", "Line Total"),
                    (dto.items() != null ? dto.items() : java.util.List.<PurchaseService.PurchaseItemDto>of()).stream()
                            .map(it -> java.util.List.of(
                                    it.productName() != null ? it.productName() : "",
                                    it.quantity() != null ? it.quantity().stripTrailingZeros().toPlainString() : "0",
                                    it.unitCostPrice() != null ? it.unitCostPrice().toPlainString() : "0.00",
                                    it.lineTotal() != null ? it.lineTotal().toPlainString() : "0.00"))
                            .toList(),
                    total != null ? total.toPlainString() : "0.00"
            );
        }

        long size = Files.size(target);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setContentLengthLong(size);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        auditService.log(AuditActions.REPORT_EXPORT, "purchase", id,
                "Exported purchase " + dto.purchaseNumber() + " as PDF");

        return out -> {
            try (InputStream in = Files.newInputStream(target)) {
                in.transferTo(out);
            }
        };
    }
}
