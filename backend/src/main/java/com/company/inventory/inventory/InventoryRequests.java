package com.company.inventory.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

public final class InventoryRequests {

    private InventoryRequests() {
    }

    public static void validateQuantity(BigDecimal quantity, String label) {
        if (quantity == null) {
            throw new com.company.inventory.common.error.BusinessRuleException(
                    "INVALID_QUANTITY", label + " is required.");
        }
        if (quantity.signum() <= 0) {
            throw new com.company.inventory.common.error.BusinessRuleException(
                    "INVALID_QUANTITY", label + " must be greater than zero.");
        }
        if (quantity.stripTrailingZeros().scale() > 3) {
            throw new com.company.inventory.common.error.BusinessRuleException(
                    "INVALID_QUANTITY", label + " supports at most 3 decimal places.");
        }
    }

    public record StockInRequest(
            @NotNull Long productId,
            @NotNull BigDecimal quantity,
            String reference,
            String notes) {

        public StockInRequest {
            validateQuantity(quantity, "Quantity");
            reference = orEmptyStatic(reference);
            notes = orEmptyStatic(notes);
        }
    }

    public record StockOutRequest(
            @NotNull Long productId,
            @NotNull BigDecimal quantity,
            String reference,
            @NotBlank(message = "Reason is required for stock out")
            String reason,
            String notes) {

        public StockOutRequest {
            validateQuantity(quantity, "Quantity");
            reference = orEmptyStatic(reference);
            reason = reason == null ? "" : reason.trim();
            notes = orEmptyStatic(notes);
        }
    }

    public record AdjustmentRequest(
            @NotNull Long productId,
            @NotNull AdjustmentDirection direction,
            @NotNull BigDecimal quantity,
            @NotBlank(message = "Reason is required for adjustments")
            String reason,
            String notes) {

        public AdjustmentRequest {
            validateQuantity(quantity, "Quantity");
            reason = reason == null ? "" : reason.trim();
            notes = orEmptyStatic(notes);
        }
    }

    private static String orEmptyStatic(String s) {
        return s == null ? "" : s;
    }

    public enum AdjustmentDirection {IN, OUT}
}
