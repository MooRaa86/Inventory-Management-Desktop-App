package com.company.inventory.report;

import com.company.inventory.common.error.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Serializes {@link ReportService.ReportTable}s into CSV, XLSX or PDF files
 * under exports/reports.
 */
@Component
public class ReportFileWriter {

    private final Path reportsDir;
    private final com.company.inventory.settings.SettingsService settingsService;

    public ReportFileWriter(com.company.inventory.startup.AppPaths paths,
                            com.company.inventory.settings.SettingsService settingsService) {
        this.reportsDir = paths.reportsExports();
        this.settingsService = settingsService;
    }

    public ReportService.GeneratedFile write(String type, ReportService.Format format,
                                             ReportService.ReportTable table) {
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String safeType = type.toLowerCase().replace('-', '_');
        String fileName = safeType + "_" + stamp + "." + extension(format);
        Path target = reportsDir.resolve(fileName);
        try {
            Files.createDirectories(reportsDir);
            switch (format) {
                case CSV -> writeCsv(target, table);
                case XLSX -> writeXlsx(target, table);
                case PDF -> writePdf(target, table);
                default -> throw new ApiException(422, "INVALID_FORMAT",
                        "JSON is returned inline; choose CSV, XLSX or PDF for a file.");
            }
        } catch (IOException e) {
            throw new ApiException(500, "REPORT_WRITE_FAILED",
                    "Could not write report file: " + e.getMessage());
        }
        long size;
        try {
            size = Files.size(target);
        } catch (IOException e) {
            size = 0;
        }
        return new ReportService.GeneratedFile(fileName, size,
                "/api/reports/files/" + fileName);
    }

    public Path resolveDownload(String fileName) {
        if (fileName == null || !fileName.matches("[A-Za-z0-9._-]+")) {
            throw new ApiException(422, "INVALID_FILE_NAME", "Invalid file name.");
        }
        Path p = reportsDir.resolve(fileName).normalize();
        if (!p.startsWith(reportsDir) || !Files.isRegularFile(p)) {
            throw ApiException.notFound("Report file not found.");
        }
        return p;
    }

    public Path getReportsDir() {
        return reportsDir;
    }

    private String extension(ReportService.Format f) {
        return switch (f) {
            case CSV -> "csv";
            case XLSX -> "xlsx";
            case PDF -> "pdf";
            default -> "json";
        };
    }

    private void writeCsv(Path target, ReportService.ReportTable t) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(target.toFile()),
                StandardCharsets.UTF_8)) {
            w.write('\ufeff'); // Excel-friendly BOM
            w.write(csvLine(t.columns()));
            for (List<String> row : t.rows()) {
                w.write(csvLine(row));
            }
        }
    }

    private String csvLine(List<String> values) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = values.get(i) == null ? "" : values.get(i);
            boolean needsQuotes = v.contains(",") || v.contains("\"")
                    || v.contains("\n") || v.contains("\r");
            if (needsQuotes) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append(System.lineSeparator());
        return sb.toString();
    }

    private void writeXlsx(Path target, ReportService.ReportTable t) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet(safeSheetName(t.type()));
            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            for (int c = 0; c < t.columns().size(); c++) {
                header.createCell(c).setCellValue(t.columns().get(c));
            }
            for (List<String> data : t.rows()) {
                Row r = sheet.createRow(rowIdx++);
                for (int c = 0; c < data.size(); c++) {
                    Cell cell = r.createCell(c);
                    cell.setCellValue(data.get(c));
                }
            }
            try (var out = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
                wb.write(out);
            }
            wb.dispose();
        }
    }

    private String safeSheetName(String type) {
        String name = type.toUpperCase();
        return name.length() > 28 ? name.substring(0, 28) : name;
    }

    private void writePdf(Path target, ReportService.ReportTable t) throws IOException {
        String companyName = settingsService.get("company.name");
        if (companyName == null || companyName.isBlank()) companyName = settingsService.get("app.name");

        // Per-report type column formatting
        List<PdfReportRenderer.ColType> colTypes = getColTypes(t.type());
        List<String> summaryRow = getSummaryRow(t);
        String dateRange = null;

        try (var out = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
            PdfReportRenderer.render(out, companyName, t.type(),
                    t.columns(), t.rows(), colTypes, summaryRow, dateRange);
        }
    }

    private List<PdfReportRenderer.ColType> getColTypes(String type) {
        return switch (type) {
            case "inventory" -> List.of(
                    PdfReportRenderer.ColType.TEXT,     // Name
                    PdfReportRenderer.ColType.TEXT,     // Category
                    PdfReportRenderer.ColType.TEXT,     // Unit
                    PdfReportRenderer.ColType.NUMBER,   // Min Stock
                    PdfReportRenderer.ColType.NUMBER,   // Current Stock
                    PdfReportRenderer.ColType.STATUS,   // Status
                    PdfReportRenderer.ColType.CURRENCY, // Cost
                    PdfReportRenderer.ColType.CURRENCY, // Sell Price
                    PdfReportRenderer.ColType.CURRENCY  // Stock Value
            );
            case "low-stock" -> List.of(
                    PdfReportRenderer.ColType.TEXT,     // Name
                    PdfReportRenderer.ColType.TEXT,     // Category
                    PdfReportRenderer.ColType.NUMBER,   // Min Stock
                    PdfReportRenderer.ColType.NUMBER,   // Current Stock
                    PdfReportRenderer.ColType.NUMBER,   // Shortage
                    PdfReportRenderer.ColType.STATUS    // Status
            );
            case "movements" -> List.of(
                    PdfReportRenderer.ColType.NUMBER,   // ID
                    PdfReportRenderer.ColType.DATE,     // Date
                    PdfReportRenderer.ColType.TEXT,     // Product
                    PdfReportRenderer.ColType.STATUS,   // Type
                    PdfReportRenderer.ColType.NUMBER,   // Qty
                    PdfReportRenderer.ColType.NUMBER,   // Before
                    PdfReportRenderer.ColType.NUMBER,   // After
                    PdfReportRenderer.ColType.TEXT,     // Reference
                    PdfReportRenderer.ColType.TEXT      // User
            );
            case "purchases" -> List.of(
                    PdfReportRenderer.ColType.TEXT,     // Purchase #
                    PdfReportRenderer.ColType.DATE,     // Date
                    PdfReportRenderer.ColType.TEXT,     // Supplier
                    PdfReportRenderer.ColType.STATUS,   // Status
                    PdfReportRenderer.ColType.CURRENCY, // Total
                    PdfReportRenderer.ColType.NUMBER,   // Lines
                    PdfReportRenderer.ColType.TEXT      // Note
            );
            case "issues" -> List.of(
                    PdfReportRenderer.ColType.TEXT,     // Issue #
                    PdfReportRenderer.ColType.DATE,     // Date
                    PdfReportRenderer.ColType.TEXT,     // Department
                    PdfReportRenderer.ColType.TEXT,     // Requested By
                    PdfReportRenderer.ColType.STATUS,   // Status
                    PdfReportRenderer.ColType.TEXT,     // Approved By
                    PdfReportRenderer.ColType.NUMBER    // Lines
            );
            case "suppliers" -> List.of(
                    PdfReportRenderer.ColType.TEXT,     // Name
                    PdfReportRenderer.ColType.TEXT,     // Phone
                    PdfReportRenderer.ColType.TEXT,     // Email
                    PdfReportRenderer.ColType.TEXT,     // Address
                    PdfReportRenderer.ColType.TEXT,     // Tax Number
                    PdfReportRenderer.ColType.STATUS    // Status
            );
            case "audit" -> List.of(
                    PdfReportRenderer.ColType.NUMBER,   // ID
                    PdfReportRenderer.ColType.DATE,     // Date
                    PdfReportRenderer.ColType.TEXT,     // User
                    PdfReportRenderer.ColType.STATUS,   // Action
                    PdfReportRenderer.ColType.TEXT,     // Entity Type
                    PdfReportRenderer.ColType.NUMBER,   // Entity ID
                    PdfReportRenderer.ColType.TEXT      // Description
            );
            default -> null;
        };
    }

    private List<String> getSummaryRow(ReportService.ReportTable t) {
        if (t.rows() == null || t.rows().isEmpty()) return null;
        return switch (t.type()) {
            case "inventory" -> {
                java.math.BigDecimal totalValue = java.math.BigDecimal.ZERO;
                for (var row : t.rows()) {
                    if (row.size() >= 9) {
                        try { totalValue = totalValue.add(new java.math.BigDecimal(row.get(8))); } catch (Exception ignored) {}
                    }
                }
                yield java.util.List.of(
                        "TOTAL (" + t.rows().size() + " products)", "", "", "", "", "", "", "",
                        totalValue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            }
            case "purchases" -> {
                java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;
                for (var row : t.rows()) {
                    if (row.size() >= 5) {
                        try { totalAmount = totalAmount.add(new java.math.BigDecimal(row.get(4))); } catch (Exception ignored) {}
                    }
                }
                yield java.util.List.of(
                        "TOTAL (" + t.rows().size() + " purchases)", "", "", "",
                        totalAmount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), "", "");
            }
            default -> null;
        };
    }
}
