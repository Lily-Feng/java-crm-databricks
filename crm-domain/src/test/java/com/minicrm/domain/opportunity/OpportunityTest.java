package com.minicrm.domain.opportunity;

import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpportunityTest {

    private static final Instant OPENED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-01-02T00:00:00Z");

    private Opportunity opened() {
        return Opportunity.open(
                OpportunityId.newId(),
                CustomerId.newId(),
                "Acme renewal",
                Money.of("USD", 100_000_00),
                Probability.of(0.2),
                "alice",
                OPENED);
    }

    @Test
    void opensInQualifiedStageAtVersionZero() {
        var opportunity = opened();

        assertThat(opportunity.stage()).isEqualTo(Stage.QUALIFIED);
        assertThat(opportunity.version()).isZero();
    }

    @Test
    void validTransitionAdvancesStageAndVersion() {
        var opportunity = opened();

        opportunity.changeStage(Stage.PROPOSAL, 0, LATER);

        assertThat(opportunity.stage()).isEqualTo(Stage.PROPOSAL);
        assertThat(opportunity.version()).isEqualTo(1);
        assertThat(opportunity.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void staleExpectedVersionIsRejectedBeforeCheckingTheTransition() {
        var opportunity = opened();
        opportunity.changeStage(Stage.PROPOSAL, 0, LATER);

        // expectedVersion 0 is now stale (current version is 1); this would also be an
        // invalid PROPOSAL -> PROPOSAL transition, but the version conflict must win so the
        // two 409 causes (stale version vs. invalid transition) stay distinguishable.
        assertThatThrownBy(() -> opportunity.changeStage(Stage.PROPOSAL, 0, LATER))
                .isInstanceOf(StaleVersionException.class);
    }

    @Test
    void invalidTransitionIsRejectedWithACurrentExpectedVersion() {
        var opportunity = opened();

        assertThatThrownBy(() -> opportunity.changeStage(Stage.CLOSED_WON, 0, LATER))
                .isInstanceOf(InvalidStageTransitionException.class);
        assertThat(opportunity.stage()).isEqualTo(Stage.QUALIFIED);
        assertThat(opportunity.version()).isZero();
    }

    @Test
    void anyOpenStageCanCloseAsLost() {
        var opportunity = opened();

        opportunity.changeStage(Stage.CLOSED_LOST, 0, LATER);

        assertThat(opportunity.stage()).isEqualTo(Stage.CLOSED_LOST);
    }

    @Test
    void closedStageIsTerminal() {
        var opportunity = opened();
        opportunity.changeStage(Stage.CLOSED_LOST, 0, LATER);

        assertThatThrownBy(() -> opportunity.changeStage(Stage.QUALIFIED, 1, LATER))
                .isInstanceOf(InvalidStageTransitionException.class);
    }
}
