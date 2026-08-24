package com.company.inventory.product;

import com.company.inventory.common.money.Money;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(max = 200)
        String name,

        @Size(max = 4000)
        String description,

        Long categoryId,

        @NotNull(message = "Unit is required")
        Long unitId,

        Long supplierId,

        @NotNull @DecimalMin(value = "0", inclusive = true, message = "Minimum stock must not be negative")
        BigDecimal minStock,

        @DecimalMin(value = "0", inclusive = true, message = "Maximum stock must not be negative")
        BigDecimal maxStock,

        @NotNull @DecimalMin(value = "0", inclusive = true, message = "Cost price must not be negative")
        BigDecimal costPrice,

        @NotNull @DecimalMin(value = "0", inclusive = true, message = "Selling price must not be negative")
        BigDecimal sellingPrice,

        /** Optional initial quantity; creates a STOCK_IN movement when > 0 (create only). */
        @DecimalMin(value = "0", inclusive = true, message = "Opening stock must not be negative")
        BigDecimal openingQuantity) {

    public long costCents() {
        return Money.toCents(costPrice, "Cost price");
    }

    public long sellCents() {
        return Money.toCents(sellingPrice, "Selling price");
    }
}
