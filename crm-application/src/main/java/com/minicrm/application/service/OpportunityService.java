package com.minicrm.application.service;

import com.minicrm.application.port.OpportunityRepository;
import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;

import java.time.Clock;
import java.util.Objects;

/**
 * Backs {@code POST /opportunities} and {@code PATCH /opportunities/{id}/stage} (§3).
 * Version-conflict and invalid-transition rejection stay in
 * {@link Opportunity#changeStage}; this service only translates "not found" into its own
 * exception and persists the result.
 */
public final class OpportunityService {

    private final OpportunityRepository opportunities;
    private final Clock clock;

    public OpportunityService(OpportunityRepository opportunities, Clock clock) {
        this.opportunities = Objects.requireNonNull(opportunities, "opportunities must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Opportunity createOpportunity(
            CustomerId customerId, String name, Money amount, Probability probability, String owner) {
        var opportunity =
                Opportunity.open(OpportunityId.newId(), customerId, name, amount, probability, owner, clock.instant());
        return opportunities.save(opportunity);
    }

    public Opportunity getOpportunity(OpportunityId id) {
        return opportunities.findById(id).orElseThrow(() -> new OpportunityNotFoundException(id));
    }

    /**
     * Throws {@link com.minicrm.domain.opportunity.StaleVersionException} or
     * {@link com.minicrm.domain.opportunity.InvalidStageTransitionException} straight
     * through from the domain (§3): both map to 409 at the REST adapter, as distinct
     * problem types.
     */
    public Opportunity changeStage(OpportunityId id, Stage targetStage, int expectedVersion) {
        var opportunity = getOpportunity(id);
        opportunity.changeStage(targetStage, expectedVersion, clock.instant());
        return opportunities.save(opportunity);
    }
}
