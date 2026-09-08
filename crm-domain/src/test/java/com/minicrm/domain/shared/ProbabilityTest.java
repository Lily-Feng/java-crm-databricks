package com.minicrm.domain.shared;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProbabilityTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 1.0})
    void acceptsValuesWithinRange(double value) {
        assertThat(Probability.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NaN})
    void rejectsValuesOutsideRange(double value) {
        assertThatThrownBy(() -> Probability.of(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
