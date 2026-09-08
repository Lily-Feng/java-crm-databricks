package com.minicrm.domain.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void rejectsNegativeMinorUnits() {
        assertThatThrownBy(() -> Money.of("USD", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsAmountsInTheSameCurrency() {
        var total = Money.of("USD", 1_000).plus(Money.of("USD", 250));

        assertThat(total).isEqualTo(Money.of("USD", 1_250));
    }

    @Test
    void refusesToCombineDifferentCurrencies() {
        assertThatThrownBy(() -> Money.of("USD", 100).plus(Money.of("EUR", 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("EUR");
    }
}
