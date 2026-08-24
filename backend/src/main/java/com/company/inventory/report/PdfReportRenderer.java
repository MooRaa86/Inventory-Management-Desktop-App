package com.company.inventory.report;

import com.lowagie.text.Document;
// Element constants: ALIGN_LEFT=0, ALIGN_CENTER=1, ALIGN_RIGHT=2
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Renders professional PDF reports with per-type styling, company header,
 * currency-aware formatting, and summary rows.
 */
public class PdfReportRenderer {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Color COLOR_PRIMARY = new Color(30, 58, 95);       // #1e3a5f
    private static final Color COLOR_HEADER_BG = new Color(52, 73, 94);     // #34495e
    private static final Color COLOR_HEADER_TEXT = Color.WHITE;
    private static final Color COLOR_ROW_EVEN = Color.WHITE;
    private static final Color COLOR_ROW_ODD = new Color(245, 248, 250);    // #f5f8fa
    private static final Color COLOR_BORDER = new Color(220, 225, 230);
    private static final Color COLOR_MUTED = new Color(120, 130, 145);
    private static final Color COLOR_TOTAL_BG = new Color(236, 240, 245);   // #ecf0f5

    // Column type hints for formatting
    public enum ColType { TEXT, CURRENCY, NUMBER, STATUS, DATE }

    /**
     * Render a professional PDF report.
     *
     * @param out           output stream
     * @param companyName   from settings
     * @param reportType    e.g. "inventory", "low-stock"
     * @param columns       column headers
     * @param rows          data rows
     * @param colTypes      per-column formatting hints (nullable = all TEXT)
     * @param summaryRow    optional total/summary row (appended at end with distinct styling)
     * @param dateRange     optional date range subtitle text
     */
    public static void render(OutputStream out, String companyName, String reportType,
                              List<String> columns, List<List<String>> rows,
                              List<ColType> colTypes, List<String> summaryRow,
                              String dateRange) {
        try {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 48, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
                    Paragraph footer = new Paragraph("Page " + w.getPageNumber(), footerFont);
                    footer.setAlignment(1);
                    PdfPTable footTable = new PdfPTable(1);
                    footTable.setTotalWidth(d.getPageSize().getWidth() - 72);
                    PdfPCell cell = new PdfPCell(footer);
                    cell.setBorder(Rectangle.NO_BORDER);
                    cell.setHorizontalAlignment(1);
                    footTable.addCell(cell);
                    footTable.writeSelectedRows(0, -1, 36, 36, w.getDirectContent());
                }
            });

            doc.open();

            // --- Company header bar ---
            PdfPTable headerBar = new PdfPTable(1);
            headerBar.setTotalWidth(doc.getPageSize().getWidth() - 72);
            Font companyFont = new Font(Font.HELVETICA, 18, Font.BOLD, COLOR_HEADER_TEXT);
            PdfPCell compCell = new PdfPCell(new Phrase("  " + (companyName != null ? companyName : "Inventory Manager"), companyFont));
            compCell.setBackgroundColor(COLOR_PRIMARY);
            compCell.setPadding(14);
            compCell.setBorder(Rectangle.NO_BORDER);
            headerBar.addCell(compCell);
            doc.add(headerBar);

            // --- Report title ---
            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD, COLOR_PRIMARY);
            String titleText = reportType.replace("-", " ").toUpperCase() + " REPORT";
            Paragraph title = new Paragraph(titleText, titleFont);
            title.setSpacingBefore(14);
            title.setSpacingAfter(4);
            doc.add(title);

            // --- Meta info line ---
            Font metaFont = new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_MUTED);
            StringBuilder meta = new StringBuilder();
            meta.append("Generated: ").append(LocalDateTime.now().format(DTF));
            meta.append("    |    ").append(rows.size()).append(" row(s)");
            if (dateRange != null && !dateRange.isBlank()) {
                meta.append("    |    ").append(dateRange);
            }
            Paragraph metaP = new Paragraph(meta.toString(), metaFont);
            metaP.setSpacingAfter(12);
            doc.add(metaP);

            // --- Data table ---
            int cols = columns.size();
            PdfPTable pdfTable = new PdfPTable(cols);
            pdfTable.setWidthPercentage(100);
            pdfTable.setHeaderRows(1);
            pdfTable.setSpacingBefore(0);
            pdfTable.setSpacingAfter(0);

            // Smart column widths based on type
            float[] widths = computeWidths(columns, colTypes, cols);
            pdfTable.setWidths(widths);

            // Header cells
            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_HEADER_TEXT);
            for (String col : columns) {
                PdfPCell hc = new PdfPCell(new Phrase(col, headerFont));
                hc.setBackgroundColor(COLOR_HEADER_BG);
                hc.setPadding(7);
                hc.setHorizontalAlignment(0);
                hc.setBorderColor(COLOR_PRIMARY);
                hc.setBorderWidth(0.5f);
                pdfTable.addCell(hc);
            }

            // Body rows
            Font bodyFont = new Font(Font.HELVETICA, 8);
            for (int r = 0; r < rows.size(); r++) {
                List<String> row = rows.get(r);
                Color bg = (r % 2 == 0) ? COLOR_ROW_EVEN : COLOR_ROW_ODD;
                for (int c = 0; c < row.size(); c++) {
                    String v = row.get(c) == null ? "" : row.get(c);
                    PdfPCell cell = new PdfPCell(new Phrase(v, bodyFont));
                    cell.setPadding(5);
                    cell.setBackgroundColor(bg);
                    cell.setBorderColor(COLOR_BORDER);
                    cell.setBorderWidth(0.5f);

                    // Right-align numbers and currency
                    ColType ct = (colTypes != null && c < colTypes.size()) ? colTypes.get(c) : ColType.TEXT;
                    if (ct == ColType.CURRENCY || ct == ColType.NUMBER) {
                        cell.setHorizontalAlignment(2);
                    }
                    if (ct == ColType.STATUS) {
                        cell.setHorizontalAlignment(1);
                        Font statusFont = getStatusFont(v);
                        cell.setPhrase(new Phrase(v, statusFont));
                    }
                    pdfTable.addCell(cell);
                }
            }

            // Summary row
            if (summaryRow != null && !summaryRow.isEmpty()) {
                Font summaryFont = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_PRIMARY);
                for (int c = 0; c < summaryRow.size(); c++) {
                    String v = summaryRow.get(c) == null ? "" : summaryRow.get(c);
                    PdfPCell cell = new PdfPCell(new Phrase(v, summaryFont));
                    cell.setPadding(6);
                    cell.setBackgroundColor(COLOR_TOTAL_BG);
                    cell.setBorderColor(COLOR_PRIMARY);
                    cell.setBorderWidth(0.8f);
                    ColType ct = (colTypes != null && c < colTypes.size()) ? colTypes.get(c) : ColType.TEXT;
                    if (ct == ColType.CURRENCY || ct == ColType.NUMBER) {
                        cell.setHorizontalAlignment(2);
                    }
                    pdfTable.addCell(cell);
                }
            }

            doc.add(pdfTable);
            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF render failed: " + e.getMessage(), e);
        }
    }

    /**
     * Render a purchase invoice PDF.
     */
    public static void renderPurchaseInvoice(OutputStream out, String companyName,
                                              String purchaseNumber, String supplierName,
                                              String purchaseDate, String status,
                                              String createdByName, String receivedInfo,
                                              String notes,
                                              List<String> itemColumns, List<List<String>> itemRows,
                                              String totalAmount) {
        try {
            Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
                    Paragraph footer = new Paragraph("Page " + w.getPageNumber(), footerFont);
                    footer.setAlignment(1);
                    PdfPTable footTable = new PdfPTable(1);
                    footTable.setTotalWidth(d.getPageSize().getWidth() - 96);
                    PdfPCell cell = new PdfPCell(footer);
                    cell.setBorder(Rectangle.NO_BORDER);
                    cell.setHorizontalAlignment(1);
                    footTable.addCell(cell);
                    footTable.writeSelectedRows(0, -1, 48, 36, w.getDirectContent());
                }
            });

            doc.open();

            // --- Company header ---
            PdfPTable headerBar = new PdfPTable(1);
            headerBar.setTotalWidth(doc.getPageSize().getWidth() - 96);
            Font companyFont = new Font(Font.HELVETICA, 18, Font.BOLD, COLOR_HEADER_TEXT);
            PdfPCell compCell = new PdfPCell(new Phrase("  " + (companyName != null ? companyName : ""), companyFont));
            compCell.setBackgroundColor(COLOR_PRIMARY);
            compCell.setPadding(14);
            compCell.setBorder(Rectangle.NO_BORDER);
            headerBar.addCell(compCell);
            doc.add(headerBar);

            // --- Title ---
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, COLOR_PRIMARY);
            Paragraph title = new Paragraph("PURCHASE ORDER", titleFont);
            title.setSpacingBefore(16);
            title.setSpacingAfter(12);
            doc.add(title);

            // --- Info grid (2 columns) ---
            Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_MUTED);
            Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setTotalWidth(doc.getPageSize().getWidth() - 96);
            infoTable.setWidths(new float[]{50, 50});

            addInfoCell(infoTable, "Purchase #:", purchaseNumber, labelFont, valueFont);
            addInfoCell(infoTable, "Date:", purchaseDate, labelFont, valueFont);
            addInfoCell(infoTable, "Supplier:", supplierName, labelFont, valueFont);
            addInfoCell(infoTable, "Status:", status, labelFont, valueFont);
            addInfoCell(infoTable, "Created by:", createdByName, labelFont, valueFont);
            if (receivedInfo != null && !receivedInfo.isBlank()) {
                addInfoCell(infoTable, "Received:", receivedInfo, labelFont, valueFont);
            } else {
                addInfoCell(infoTable, "", "", labelFont, valueFont);
            }
            if (notes != null && !notes.isBlank()) {
                addInfoCell(infoTable, "Notes:", notes, labelFont, valueFont);
                addInfoCell(infoTable, "", "", labelFont, valueFont);
            }

            doc.add(infoTable);
            doc.add(new Paragraph(" "));

            // --- Items table ---
            int cols = itemColumns.size();
            PdfPTable itemsTable = new PdfPTable(cols);
            itemsTable.setWidthPercentage(100);
            itemsTable.setHeaderRows(1);
            itemsTable.setWidths(computeWidths(itemColumns, null, cols));

            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_HEADER_TEXT);
            for (String col : itemColumns) {
                PdfPCell hc = new PdfPCell(new Phrase(col, headerFont));
                hc.setBackgroundColor(COLOR_HEADER_BG);
                hc.setPadding(7);
                hc.setHorizontalAlignment(0);
                hc.setBorderColor(COLOR_PRIMARY);
                hc.setBorderWidth(0.5f);
                itemsTable.addCell(hc);
            }

            Font bodyFont = new Font(Font.HELVETICA, 9);
            for (int r = 0; r < itemRows.size(); r++) {
                List<String> row = itemRows.get(r);
                Color bg = (r % 2 == 0) ? COLOR_ROW_EVEN : COLOR_ROW_ODD;
                for (int c = 0; c < row.size(); c++) {
                    String v = row.get(c) == null ? "" : row.get(c);
                    PdfPCell cell = new PdfPCell(new Phrase(v, bodyFont));
                    cell.setPadding(6);
                    cell.setBackgroundColor(bg);
                    cell.setBorderColor(COLOR_BORDER);
                    cell.setBorderWidth(0.5f);
                    // Right-align qty and money columns
                    if (c >= 1) {
                        cell.setHorizontalAlignment(2);
                    }
                    itemsTable.addCell(cell);
                }
            }

            // Total row
            Font totalFont = new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_PRIMARY);
            PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL", totalFont));
            totalLabelCell.setPadding(8);
            totalLabelCell.setBackgroundColor(COLOR_TOTAL_BG);
            totalLabelCell.setBorderColor(COLOR_PRIMARY);
            totalLabelCell.setBorderWidth(0.8f);
            totalLabelCell.setHorizontalAlignment(2);
            itemsTable.addCell(totalLabelCell);

            // Empty cells for middle columns
            for (int i = 1; i < cols - 1; i++) {
                PdfPCell empty = new PdfPCell(new Phrase(""));
                empty.setBackgroundColor(COLOR_TOTAL_BG);
                empty.setBorderColor(COLOR_PRIMARY);
                empty.setBorderWidth(0.8f);
                empty.setPadding(8);
                itemsTable.addCell(empty);
            }

            PdfPCell totalValueCell = new PdfPCell(new Phrase(totalAmount, totalFont));
            totalValueCell.setPadding(8);
            totalValueCell.setBackgroundColor(COLOR_TOTAL_BG);
            totalValueCell.setBorderColor(COLOR_PRIMARY);
            totalValueCell.setBorderWidth(0.8f);
            totalValueCell.setHorizontalAlignment(2);
            itemsTable.addCell(totalValueCell);

            doc.add(itemsTable);
            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF render failed: " + e.getMessage(), e);
        }
    }

    private static void addInfoCell(PdfPTable table, String label, String value,
                                     Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "" : value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(3);
        table.addCell(valueCell);
    }

    private static float[] computeWidths(List<String> columns, List<ColType> colTypes, int cols) {
        float[] w = new float[cols];
        for (int i = 0; i < cols; i++) {
            ColType ct = (colTypes != null && i < colTypes.size()) ? colTypes.get(i) : ColType.TEXT;
            String name = columns.get(i).toLowerCase();
            float base = switch (ct) {
                case CURRENCY -> 12f;
                case NUMBER -> 9f;
                case STATUS -> 10f;
                case DATE -> 14f;
                default -> 15f;
            };
            // Give more width to name/description columns
            if (name.contains("name") || name.contains("product") || name.contains("description")
                    || name.contains("supplier") || name.contains("department")) {
                base = 22f;
            } else if (name.contains("reference") || name.contains("note")) {
                base = 18f;
            }
            w[i] = base;
        }
        // Normalize to 100%
        float sum = 0;
        for (float v : w) sum += v;
        for (int i = 0; i < cols; i++) w[i] = (w[i] / sum) * 100f;
        return w;
    }

    private static Font getStatusFont(String status) {
        Color c = switch (status.toUpperCase().replace(" ", "_")) {
            case "IN_STOCK", "RECEIVED", "COMPLETED", "ACTIVE", "SUCCESS" -> new Color(46, 158, 91);
            case "LOW_STOCK", "PENDING", "APPROVED" -> new Color(217, 122, 6);
            case "OUT_OF_STOCK", "CANCELLED", "FAILED" -> new Color(207, 53, 53);
            default -> new Color(52, 152, 219);
        };
        return new Font(Font.HELVETICA, 8, Font.BOLD, c);
    }
}
