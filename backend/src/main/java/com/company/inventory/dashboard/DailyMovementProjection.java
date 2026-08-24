package com.company.inventory.dashboard;

import java.math.BigDecimal;

public interface DailyMovementProjection {
    String getDay();

    String getMovementType();

    BigDecimal getTotal();
}
