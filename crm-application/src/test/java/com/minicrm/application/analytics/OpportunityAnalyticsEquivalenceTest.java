package com.minicrm.application.analytics;

import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * idea.md §11: implement CRM analytics with loops first, then Streams, and treat them as
 * two implementations of the same aggregate rather than one "real" and one demo — proven
 * here by asserting both produce identical results on the same fixture data.
 */
class OpportunityAnalyticsEquivalenceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private final List<Opportunity> opportunities = List.of(
            opportunity("A", USD, 10_000_00, "alice", Stage.QUALIFIED),
            opportunity("B", USD, 30_000_00, "alice", Stage.PROPOSAL),
            opportunity("C", USD, 5_000_00, "bob", Stage.CLOSED_WON, Stage.PROPOSAL, Stage.NEGOTIATION),
            opportunity("D", EUR, 20_000_00, "bob", Stage.QUALIFIED));

    private static Opportunity opportunity(String name, Currency currency, long minorUnits, String owner, Stage finalStage,
                                            Stage... path) {
        var opportunity = Opportunity.open(
                OpportunityId.newId(), CustomerId.newId(), name, new Money(currency, minorUnits),
                Probability.of(0.3), owner, NOW);
        for (Stage step : path) {
            opportunity.changeStage(step, opportunity.version(), NOW);
        }
        if (opportunity.stage() != finalStage) {
            opportunity.changeStage(finalStage, opportunity.version(), NOW);
        }
        return opportunity;
    }

    private static Opportunity opportunity(String name, Currency currency, long minorUnits, String owner, Stage finalStage) {
        return opportunity(name, currency, minorUnits, owner, finalStage, new Stage[0]);
    }

    @Test
    void revenueByOwnerMatches() {
        assertThat(OpportunityAnalyticsLoops.revenueByOwner(opportunities))
                .isEqualTo(OpportunityAnalyticsStreams.revenueByOwner(opportunities));
    }

    @Test
    void averageOpportunitySizeMatches() {
        assertThat(OpportunityAnalyticsLoops.averageOpportunitySizeMinorUnits(opportunities))
                .isEqualTo(OpportunityAnalyticsStreams.averageOpportunitySizeMinorUnits(opportunities));
    }

    @Test
    void topOpportunitiesMatches() {
        assertThat(OpportunityAnalyticsLoops.topOpportunities(opportunities, USD, 2))
                .isEqualTo(OpportunityAnalyticsStreams.topOpportunities(opportunities, USD, 2));
    }

    @Test
    void conversionByStageMatches() {
        assertThat(OpportunityAnalyticsLoops.conversionByStage(opportunities))
                .isEqualTo(OpportunityAnalyticsStreams.conversionByStage(opportunities));
    }

    @Test
    void topOpportunitiesRanksByAmountDescendingWithinACurrency() {
        var top = OpportunityAnalyticsStreams.topOpportunities(opportunities, USD, 2);

        assertThat(top).extracting(Opportunity::name).containsExactly("B", "A");
    }
}
