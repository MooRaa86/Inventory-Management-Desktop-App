package com.company.inventory.common.money;

import com.company.inventory.common.error.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    /** Converts an amount to integer cents. Rejects more than 2 decimal places and negatives. */
    public static long toCents(BigDecimal amount, String fieldLabel) {
        if (amount == null) {
            return 0L;
        }
        if (amount.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", fieldLabel + " must not be negative.");
        }
        BigDecimal normalized = amount.setScale(4, RoundingMode.UNNECESSARY);
        if (normalized.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("TOO_MANY_DECIMALS",
                    fieldLabel + " supports at most 2 decimal places.");
        }
        return normalized.multiply(HUNDRED).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
