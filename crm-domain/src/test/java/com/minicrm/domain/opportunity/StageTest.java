package com.minicrm.domain.opportunity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class StageTest {

    @ParameterizedTest
    @CsvSource({
            "QUALIFIED, PROPOSAL, true",
            "PROPOSAL, NEGOTIATION, true",
            "NEGOTIATION, CLOSED_WON, true",
            "QUALIFIED, NEGOTIATION, false",
            "QUALIFIED, CLOSED_WON, false",
            "PROPOSAL, QUALIFIED, false",
            "QUALIFIED, CLOSED_LOST, true",
            "PROPOSAL, CLOSED_LOST, true",
            "NEGOTIATION, CLOSED_LOST, true",
            "CLOSED_WON, CLOSED_LOST, false",
            "CLOSED_LOST, CLOSED_LOST, false",
            "CLOSED_WON, QUALIFIED, false",
    })
    void followsTheAllowedProgression(Stage from, Stage to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = Stage.class, names = {"CLOSED_WON", "CLOSED_LOST"})
    void closedStagesAreTerminal(Stage stage) {
        assertThat(stage.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Stage.class, names = {"QUALIFIED", "PROPOSAL", "NEGOTIATION"})
    void openStagesAreNotTerminal(Stage stage) {
        assertThat(stage.isTerminal()).isFalse();
    }
}
