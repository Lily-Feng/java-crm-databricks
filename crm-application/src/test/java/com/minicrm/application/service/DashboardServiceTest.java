package com.minicrm.application.service;

import com.minicrm.application.analytics.OwnerCurrency;
import com.minicrm.application.analytics.StageCurrency;
import com.minicrm.application.service.support.FakeOpportunityRepository;
import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Currency USD = Currency.getInstance("USD");

    private final FakeOpportunityRepository opportunities = new FakeOpportunityRepository();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DashboardService service = new DashboardService(opportunities, clock);

    @Test
    void summarizesTotalsByStageAndOwner() {
        opportunities.save(Opportunity.open(
                OpportunityId.newId(), CustomerId.newId(), "A", Money.of("USD", 10_000_00),
                Probability.of(0.2), "alice", NOW));
        opportunities.save(Opportunity.open(
                OpportunityId.newId(), CustomerId.newId(), "B", Money.of("USD", 5_000_00),
                Probability.of(0.2), "alice", NOW));

        var summary = service.pipelineSummary();

        assertThat(summary.totalsByStage()).containsEntry(new StageCurrency(Stage.QUALIFIED, USD), Money.of("USD", 15_000_00));
        assertThat(summary.totalsByOwner()).containsEntry(new OwnerCurrency("alice", USD), Money.of("USD", 15_000_00));
        assertThat(summary.source()).isEqualTo("local");
        assertThat(summary.dataAsOf()).isEqualTo(NOW);
    }
}
