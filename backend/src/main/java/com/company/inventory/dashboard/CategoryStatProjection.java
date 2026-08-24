package com.company.inventory.dashboard;

public interface CategoryStatProjection {
    String getName();

    Long getProductCount();

    java.math.BigDecimal getTotalStock();
}
