package com.company.inventory.dashboard;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only aggregate queries for the dashboard. All statistics are computed
 * in the database - never by loading entities into memory.
 */
public interface DashboardRepository extends Repository<com.company.inventory.product.Product, Long> {

    @Query("SELECT COUNT(p) FROM Product p")
    long countProducts();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true")
    long countActiveProducts();

    @Query("SELECT COALESCE(SUM(p.currentStock), 0) FROM Product p")
    BigDecimal sumTotalStock();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true " +
            "AND p.currentStock > 0 AND p.currentStock <= p.minStock")
    long countLowStock();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true AND p.currentStock = 0")
    long countOutOfStock();

    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM StockMovement m " +
            "WHERE m.movementType = :type AND m.createdAt >= :since")
    BigDecimal sumMovementQuantity(@Param("type") String type, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(s) FROM Supplier s WHERE s.active = true")
    long countActiveSuppliers();

    @Query("SELECT COUNT(s) FROM Supplier s")
    long countSuppliers();

    @Query("SELECT COUNT(p) FROM Purchase p WHERE p.status = 'PENDING'")
    long countPendingPurchases();

    @Query("""
            SELECT CAST(substr(m.createdAt, 1, 10) AS string) AS day,
                   m.movementType AS movementType,
                   SUM(m.quantity) AS total
            FROM StockMovement m
            WHERE m.createdAt >= :since
              AND (m.movementType = 'STOCK_IN' OR m.movementType = 'STOCK_OUT')
            GROUP BY substr(m.createdAt, 1, 10), m.movementType
            ORDER BY day ASC
            """)
    List<DailyMovementProjection> dailyMovements(@Param("since") LocalDateTime since);

    @Query("""
            SELECT COALESCE(c.name, '(No category)') AS name,
                   COUNT(p.id) AS productCount,
                   COALESCE(SUM(p.currentStock), 0) AS totalStock
            FROM Product p LEFT JOIN p.category c
            GROUP BY c.name
            ORDER BY productCount DESC
            """)
    List<CategoryStatProjection> productsByCategory();

    @Query("""
            SELECT p.id AS id, p.name AS name,
                   c.name AS categoryName, u.symbol AS unitSymbol,
                   p.minStock AS minStock, p.currentStock AS currentStock,
                   CASE WHEN p.currentStock = 0 THEN 'OUT_OF_STOCK'
                        ELSE 'LOW_STOCK' END AS status
            FROM Product p
            LEFT JOIN p.category c
            JOIN p.unit u
            WHERE p.active = true AND p.currentStock <= p.minStock
            ORDER BY (p.minStock - p.currentStock) DESC, p.name ASC
            """)
    List<LowStockProjection> lowStockProducts();
}
