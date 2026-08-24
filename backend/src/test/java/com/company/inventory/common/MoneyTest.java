package com.company.inventory.common;

import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.common.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void roundTripsExactly() {
        assertThat(Money.toCents(new BigDecimal("123.45"), "price")).isEqualTo(12345L);
        assertThat(Money.fromCents(12345L)).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(Money.toCents(null, "price")).isZero();
        assertThat(Money.toCents(new BigDecimal("0.01"), "price")).isEqualTo(1L);
        assertThat(Money.toCents(BigDecimal.ZERO, "price")).isZero();
    }

    @Test
    void rejectsNegativeAndExtraDecimals() {
        assertThatThrownBy(() -> Money.toCents(new BigDecimal("-1.00"), "price"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NEGATIVE_AMOUNT"));
        assertThatThrownBy(() -> Money.toCents(new BigDecimal("1.234"), "price"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("TOO_MANY_DECIMALS"));
    }
}
