package com.company.inventory.dashboard;

import com.company.inventory.audit.AuditLogRepository;
import com.company.inventory.backup.BackupStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final com.company.inventory.inventory.StockMovementRepository movementRepository;
    private final com.company.inventory.purchase.PurchaseRepository purchaseRepository;
    private final com.company.inventory.issue.IssueRepository issueRepository;
    private final BackupStatsService backupStatsService;

    public record RecentMovementDto(Long id, Long productId,
                                    String movementType, String quantity,
                                    String previousStock, String newStock,
                                    String reference, String username, LocalDateTime createdAt) {
    }

    public record DashboardDto(
            long totalProducts,
            long activeProducts,
            BigDecimal totalStockQuantity,
            long lowStockCount,
            long outOfStockCount,
            BigDecimal todayStockIn,
            BigDecimal todayStockOut,
            BigDecimal monthStockIn,
            BigDecimal monthStockOut,
            long totalSuppliers,
            long activeSuppliers,
            long pendingPurchases,
            BackupHealth backup,
            List<RecentMovementDto> recentMovements,
            List<RecentPurchaseDto> recentPurchases,
            List<RecentIssueDto> recentIssues,
            List<DailyPoint> stockInOutChart,
            List<CategoryStat> productsByCategoryChart,
            List<LowStockRow> lowStockProducts) {

        public record BackupHealth(String databaseStatus, LocalDateTime lastSuccessfulBackupAt,
                                   long successfulBackupCount, boolean overdueWarning) {
        }

        public record RecentPurchaseDto(Long id, String purchaseNumber, String supplierName,
                                        String status, String totalAmount, LocalDateTime createdAt) {
        }

        public record RecentIssueDto(Long id, String issueNumber, String department,
                                     String status, LocalDateTime createdAt) {
        }

        public record DailyPoint(String date, BigDecimal stockIn, BigDecimal stockOut) {
        }

        public record CategoryStat(String name, Long productCount, BigDecimal totalStock) {
        }

        public record LowStockRow(Long id, String name, String categoryName,
                                  String unitSymbol, String minStock, String currentStock,
                                  String status) {
        }
    }

    @Transactional(readOnly = true)
    public DashboardDto getDashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();

        var backupStats = backupStatsService.getStats();
        var backup = new DashboardDto.BackupHealth(
                "HEALTHY",
                backupStats.lastSuccessfulBackupAt(),
                backupStats.successfulCount(),
                backupStats.overdue());

        Map<String, BigDecimal[]> daily = new LinkedHashMap<>();
        for (var row : dashboardRepository.dailyMovements(weekStart)) {
            BigDecimal[] pair = daily.computeIfAbsent(row.getDay(),
                    d -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("STOCK_IN".equals(row.getMovementType())) {
                pair[0] = row.getTotal();
            } else {
                pair[1] = row.getTotal();
            }
        }
        List<DashboardDto.DailyPoint> chart = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : daily.entrySet()) {
            chart.add(new DashboardDto.DailyPoint(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }

        var categories = dashboardRepository.productsByCategory().stream()
                .map(c -> new DashboardDto.CategoryStat(c.getName(), c.getProductCount(), c.getTotalStock()))
                .toList();

        var lowStock = dashboardRepository.lowStockProducts().stream()
                .map(l -> new DashboardDto.LowStockRow(l.getId(), l.getName(),
                        l.getCategoryName(), l.getUnitSymbol(),
                        l.getMinStock().toPlainString(), l.getCurrentStock().toPlainString(),
                        l.getStatus()))
                .toList();

        var movements = movementRepository.findAll(
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))).getContent().stream()
                .map(m -> new RecentMovementDto(m.getId(), m.getProductId(),
                        m.getMovementType(), m.getQuantity().toPlainString(),
                        m.getPreviousStock().toPlainString(), m.getNewStock().toPlainString(),
                        m.getReference(), m.getUsername(), m.getCreatedAt()))
                .toList();

        var purchases = purchaseRepository.search(null, null, null, null, null,
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id"))).getContent().stream()
                .map(p -> new DashboardDto.RecentPurchaseDto(p.getId(), p.getPurchaseNumber(),
                        p.getSupplier().getName(), p.getStatus().name(),
                        com.company.inventory.common.money.Money.fromCents(p.getTotalCents())
                                .toPlainString(),
                        p.getCreatedAt()))
                .toList();

        var issues = issueRepository.search(null, null, null,
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id"))).getContent().stream()
                .map(i -> new DashboardDto.RecentIssueDto(i.getId(), i.getIssueNumber(),
                        i.getDepartment(), i.getStatus().name(), i.getCreatedAt()))
                .toList();

        return new DashboardDto(
                dashboardRepository.countProducts(),
                dashboardRepository.countActiveProducts(),
                dashboardRepository.sumTotalStock(),
                dashboardRepository.countLowStock(),
                dashboardRepository.countOutOfStock(),
                nz(dashboardRepository.sumMovementQuantity("STOCK_IN", dayStart)),
                nz(dashboardRepository.sumMovementQuantity("STOCK_OUT", dayStart)),
                nz(dashboardRepository.sumMovementQuantity("STOCK_IN", monthStart)),
                nz(dashboardRepository.sumMovementQuantity("STOCK_OUT", monthStart)),
                dashboardRepository.countSuppliers(),
                dashboardRepository.countActiveSuppliers(),
                dashboardRepository.countPendingPurchases(),
                backup,
                movements, purchases, issues,
                chart, categories, lowStock);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
