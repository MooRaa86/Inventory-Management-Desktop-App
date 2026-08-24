package com.company.inventory.product;

import com.company.inventory.common.money.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductDto(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        Long unitId,
        String unitName,
        String unitSymbol,
        Long supplierId,
        String supplierName,
        BigDecimal minStock,
        BigDecimal maxStock,
        BigDecimal currentStock,
        String stockStatus,
        boolean lowStock,
        long costCents,
        long sellCents,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static final String IN_STOCK = "IN_STOCK";
    public static final String LOW_STOCK = "LOW_STOCK";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";

    public static String statusOf(BigDecimal currentStock, BigDecimal minStock) {
        if (currentStock.compareTo(BigDecimal.ZERO) == 0) {
            return OUT_OF_STOCK;
        }
        return currentStock.compareTo(minStock) <= 0 ? LOW_STOCK : IN_STOCK;
    }

    static ProductDto from(Product p) {
        return new ProductDto(
                p.getId(), p.getName(),
                p.getDescription(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getUnit().getId(), p.getUnit().getName(), p.getUnit().getSymbol(),
                p.getSupplier() != null ? p.getSupplier().getId() : null,
                p.getSupplier() != null ? p.getSupplier().getName() : null,
                p.getMinStock(), p.getMaxStock(), p.getCurrentStock(),
                statusOf(p.getCurrentStock(), p.getMinStock()),
                statusOf(p.getCurrentStock(), p.getMinStock()).equals(LOW_STOCK)
                        || statusOf(p.getCurrentStock(), p.getMinStock()).equals(OUT_OF_STOCK),
                p.getCostCents(), p.getSellCents(),
                Money.fromCents(p.getCostCents()), Money.fromCents(p.getSellCents()),
                p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
