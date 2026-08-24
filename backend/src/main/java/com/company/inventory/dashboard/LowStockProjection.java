package com.company.inventory.dashboard;

import java.math.BigDecimal;

public interface LowStockProjection {
    Long getId();

    String getName();

    String getCategoryName();

    String getUnitSymbol();

    BigDecimal getMinStock();

    BigDecimal getCurrentStock();

    String getStatus();
}
