package com.minicrm.application.port;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerQueryTest {

    @Test
    void rejectsALimitBelowOne() {
        assertThatThrownBy(() -> new CustomerQuery(Optional.empty(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsALimitAboveTheMaximum() {
        assertThatThrownBy(() -> new CustomerQuery(Optional.empty(), CustomerQuery.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofTreatsABlankSearchAsAbsent() {
        assertThat(CustomerQuery.of("   ").search()).isEmpty();
    }

    @Test
    void ofKeepsANonBlankSearch() {
        assertThat(CustomerQuery.of("acme").search()).contains("acme");
    }
}
