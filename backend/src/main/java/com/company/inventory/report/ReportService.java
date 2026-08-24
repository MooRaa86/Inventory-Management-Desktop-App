package com.company.inventory.report;

import com.company.inventory.common.error.ApiException;
import com.company.inventory.common.money.Money;
import com.company.inventory.inventory.StockMovement;
import com.company.inventory.issue.Issue;
import com.company.inventory.product.Product;
import com.company.inventory.purchase.Purchase;
import com.company.inventory.supplier.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds tabular data for every supported report type. Rows are plain strings
 * so CSV/XLSX/PDF writers stay dumb and format-agnostic.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final com.company.inventory.product.ProductRepository productRepository;
    private final com.company.inventory.inventory.StockMovementRepository movementRepository;
    private final com.company.inventory.purchase.PurchaseRepository purchaseRepository;
    private final com.company.inventory.issue.IssueRepository issueRepository;
    private final com.company.inventory.supplier.SupplierRepository supplierRepository;
    private final com.company.inventory.audit.AuditLogRepository auditLogRepository;

    public enum Format {JSON, CSV, XLSX, PDF}

    public record ReportTable(String type, List<String> columns, List<List<String>> rows) {
    }

    public record ReportParams(LocalDate from, LocalDate to, Long productId, Long categoryId,
                               Long supplierId, String username) {
    }

    public record GeneratedFile(String fileName, long sizeBytes, String downloadUrl) {
    }

    public static final Set<String> TYPES = Set.of(
            "inventory", "low-stock", "movements", "purchases", "issues",
            "suppliers", "audit");

    @Transactional(readOnly = true)
    public ReportTable build(String type, ReportParams p) {
        return switch (type) {
            case "inventory" -> inventory(p);
            case "low-stock" -> lowStock();
            case "movements" -> movements(p);
            case "purchases" -> purchases(p);
            case "issues" -> issues(p);
            case "suppliers" -> suppliers();
            case "audit" -> audit(p);
            default -> throw new ApiException(422, "UNKNOWN_REPORT_TYPE",
                    "Unknown report type: " + type + ". Valid: " + TYPES);
        };
    }

    private ReportTable inventory(ReportParams p) {
        var page = productRepository.search(null, null, null, null, null, null,
                PageRequest.of(0, Integer.MAX_VALUE / 2, Sort.by("name")));
        List<List<String>> rows = new ArrayList<>();
        for (Product prod : page.getContent()) {
            if (prod.getCurrentStock() == null) {
                continue;
            }
            BigDecimal value = Money.fromCents(prod.getCostCents())
                    .multiply(prod.getCurrentStock()).setScale(2, RoundingMode.HALF_UP);
            rows.add(List.of(
                    nz(prod.getName()),
                    prod.getCategory() == null ? "" : prod.getCategory().getName(),
                    prod.getUnit() == null ? "" : prod.getUnit().getSymbol(),
                    num(prod.getMinStock()), num(prod.getCurrentStock()),
                    statusOf(prod),
                    cents(prod.getCostCents()), cents(prod.getSellCents()),
                    value.toPlainString()));
        }
        return new ReportTable("inventory",
                List.of("Name", "Category", "Unit", "Min Stock", "Current Stock",
                        "Status", "Cost", "Sell Price", "Stock Value"),
                rows);
    }

    private String statusOf(Product p) {
        BigDecimal cur = p.getCurrentStock();
        if (cur.compareTo(BigDecimal.ZERO) == 0) {
            return "OUT_OF_STOCK";
        }
        return cur.compareTo(nzv(p.getMinStock())) <= 0 ? "LOW_STOCK" : "IN_STOCK";
    }

    private ReportTable lowStock() {
        var page = productRepository.search(null, null, null, null, null, null,
                PageRequest.of(0, Integer.MAX_VALUE / 2, Sort.by("name")));
        List<List<String>> rows = new ArrayList<>();
        for (Product prod : page.getContent()) {
            if (prod.getCurrentStock() == null || !prod.isActive()) {
                continue;
            }
            BigDecimal min = nzv(prod.getMinStock());
            if (prod.getCurrentStock().compareTo(min) > 0) {
                continue;
            }
            rows.add(List.of(nz(prod.getName()),
                    prod.getCategory() == null ? "" : prod.getCategory().getName(),
                    num(min), num(prod.getCurrentStock()),
                    min.subtract(prod.getCurrentStock()).toPlainString(),
                    prod.getCurrentStock().compareTo(BigDecimal.ZERO) == 0
                            ? "OUT_OF_STOCK" : "LOW_STOCK"));
        }
        return new ReportTable("low-stock",
                List.of("Name", "Category", "Min Stock", "Current Stock", "Shortage",
                        "Status"),
                rows);
    }

    private ReportTable movements(ReportParams p) {
        LocalDateTime from = startOfDay(p.from());
        LocalDateTime to = endOfDay(p.to());
        int pageIdx = 0;
        List<List<String>> rows = new ArrayList<>();
        PageRequest pr;
        do {
            pr = PageRequest.of(pageIdx++, 500, Sort.by(Sort.Direction.ASC, "id"));
            var page = movementRepository.search(p.productId(), null, p.username(),
                    null, from, to, pr);
            for (StockMovement m : page.getContent()) {
                String productName = m.getProductId() != null
                        ? productRepository.findById(m.getProductId())
                                .map(Product::getName).orElse("") : "";
                rows.add(List.of(
                        String.valueOf(m.getId()),
                        m.getCreatedAt() == null ? "" : m.getCreatedAt().format(DTF),
                        nz(productName),
                        m.getMovementType(),
                        num(m.getQuantity()), num(m.getPreviousStock()), num(m.getNewStock()),
                        nz(m.getReference()), nz(m.getUsername())));
            }
            if (!page.hasNext()) {
                break;
            }
        } while (true);
        return new ReportTable("movements",
                List.of("ID", "Date", "Product", "Type", "Qty", "Before", "After",
                        "Reference", "User"),
                rows);
    }

    private ReportTable purchases(ReportParams p) {
        LocalDate from = p.from() == null ? LocalDate.of(2000, 1, 1) : p.from();
        LocalDate to = p.to() == null ? LocalDate.now() : p.to();
        int idx = 0;
        List<List<String>> rows = new ArrayList<>();
        PageRequest pr;
        do {
            pr = PageRequest.of(idx++, 500, Sort.by(Sort.Direction.ASC, "id"));
            var page = purchaseRepository.search(null, p.supplierId(), null, from, to, pr);
            for (Purchase pu : page.getContent()) {
                rows.add(List.of(nz(pu.getPurchaseNumber()),
                        pu.getPurchaseDate() == null ? "" : pu.getPurchaseDate().toString(),
                        nz(pu.getSupplier().getName()), pu.getStatus().name(),
                        cents(pu.getTotalCents()),
                        String.valueOf(pu.getItems() == null ? 0 : pu.getItems().size()),
                        nz(pu.getNotes())));
            }
            if (!page.hasNext()) {
                break;
            }
        } while (true);
        return new ReportTable("purchases",
                List.of("Purchase #", "Date", "Supplier", "Status", "Total", "Lines", "Note"),
                rows);
    }

    private ReportTable issues(ReportParams p) {
        int idx = 0;
        List<List<String>> rows = new ArrayList<>();
        PageRequest pr;
        do {
            pr = PageRequest.of(idx++, 500, Sort.by(Sort.Direction.ASC, "id"));
            var page = issueRepository.search(null, null, null, pr);
            for (Issue is : page.getContent()) {
                if (p.from() != null && is.getCreatedAt().toLocalDate().isBefore(p.from())) {
                    continue;
                }
                if (p.to() != null && is.getCreatedAt().toLocalDate().isAfter(p.to())) {
                    continue;
                }
                if (p.username() != null && !p.username().isBlank()
                        && (is.getDepartment() == null
                        || !is.getDepartment().toLowerCase()
                        .contains(p.username().toLowerCase()))) {
                    continue;
                }
                rows.add(List.of(nz(is.getIssueNumber()),
                        is.getCreatedAt() == null ? "" : is.getCreatedAt().format(DTF),
                        nz(is.getDepartment()), nz(is.getRequestedBy()),
                        is.getStatus().name(),
                        is.getApprovedBy() == null ? "" : nz(is.getApprovedBy().getFullName()),
                        String.valueOf(is.getItems() == null ? 0 : is.getItems().size())));
            }
            if (!page.hasNext()) {
                break;
            }
        } while (true);
        return new ReportTable("issues",
                List.of("Issue #", "Date", "Department", "Requested By", "Status",
                        "Approved By", "Lines"),
                rows);
    }

    private ReportTable suppliers() {
        List<List<String>> rows = new ArrayList<>();
        for (Supplier s : supplierRepository.findAll(Sort.by("name"))) {
            rows.add(List.of(nz(s.getName()), nz(s.getPhone()), nz(s.getEmail()),
                    nz(s.getAddress()), nz(s.getTaxNumber()),
                    s.isActive() ? "ACTIVE" : "INACTIVE"));
        }
        return new ReportTable("suppliers",
                List.of("Name", "Phone", "Email", "Address", "Tax Number", "Status"),
                rows);
    }

    private ReportTable audit(ReportParams p) {
        LocalDateTime from = startOfDay(p.from());
        LocalDateTime to = endOfDay(p.to());
        int idx = 0;
        List<List<String>> rows = new ArrayList<>();
        PageRequest pr;
        do {
            pr = PageRequest.of(idx++, 500, Sort.by(Sort.Direction.ASC, "id"));
            var page = auditLogRepository.search(p.username(), null, null, from, to, pr);
            for (var a : page.getContent()) {
                rows.add(List.of(String.valueOf(a.getId()),
                        a.getCreatedAt() == null ? "" : a.getCreatedAt().format(DTF),
                        nz(a.getUsername()), nz(a.getAction()), nz(a.getEntityType()),
                        nz(a.getEntityId()), nz(a.getDescription())));
            }
            if (!page.hasNext()) {
                break;
            }
        } while (true);
        return new ReportTable("audit",
                List.of("ID", "Date", "User", "Action", "Entity Type", "Entity ID",
                        "Description"),
                rows);
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    private BigDecimal nzv(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String num(BigDecimal v) {
        return nzv(v).stripTrailingZeros().toPlainString();
    }

    private String cents(long centsValue) {
        return Money.fromCents(centsValue).toPlainString();
    }

    private LocalDateTime startOfDay(LocalDate d) {
        return (d == null ? LocalDate.of(2000, 1, 1) : d).atStartOfDay();
    }

    private LocalDateTime endOfDay(LocalDate d) {
        return (d == null ? LocalDate.now() : d).plusDays(1).atStartOfDay().minusNanos(1);
    }
}
