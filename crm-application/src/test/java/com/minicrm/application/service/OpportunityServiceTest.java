package com.minicrm.application.service;

import com.minicrm.application.service.support.FakeOpportunityRepository;
import com.minicrm.domain.opportunity.InvalidStageTransitionException;
import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.opportunity.StaleVersionException;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpportunityServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final OpportunityService service = new OpportunityService(new FakeOpportunityRepository(), clock);

    @Test
    void createsAnOpportunityInQualifiedStage() {
        var created = service.createOpportunity(
                CustomerId.newId(), "Acme renewal", Money.of("USD", 10_000_00), Probability.of(0.3), "alice");

        assertThat(created.stage()).isEqualTo(Stage.QUALIFIED);
        assertThat(service.getOpportunity(created.id())).isSameAs(created);
    }

    @Test
    void missingOpportunityRaisesNotFound() {
        assertThatThrownBy(() -> service.getOpportunity(OpportunityId.newId()))
                .isInstanceOf(OpportunityNotFoundException.class);
    }

    @Test
    void changeStageAdvancesAndPersistsTheNewVersion() {
        var created = service.createOpportunity(
                CustomerId.newId(), "Acme renewal", Money.of("USD", 10_000_00), Probability.of(0.3), "alice");

        var updated = service.changeStage(created.id(), Stage.PROPOSAL, 0);

        assertThat(updated.stage()).isEqualTo(Stage.PROPOSAL);
        assertThat(updated.version()).isEqualTo(1);
        assertThat(service.getOpportunity(created.id()).stage()).isEqualTo(Stage.PROPOSAL);
    }

    @Test
    void staleExpectedVersionSurfacesFromTheDomain() {
        var created = service.createOpportunity(
                CustomerId.newId(), "Acme renewal", Money.of("USD", 10_000_00), Probability.of(0.3), "alice");
        service.changeStage(created.id(), Stage.PROPOSAL, 0);

        assertThatThrownBy(() -> service.changeStage(created.id(), Stage.NEGOTIATION, 0))
                .isInstanceOf(StaleVersionException.class);
    }

    @Test
    void invalidTransitionSurfacesFromTheDomain() {
        var created = service.createOpportunity(
                CustomerId.newId(), "Acme renewal", Money.of("USD", 10_000_00), Probability.of(0.3), "alice");

        assertThatThrownBy(() -> service.changeStage(created.id(), Stage.CLOSED_WON, 0))
                .isInstanceOf(InvalidStageTransitionException.class);
    }
}
