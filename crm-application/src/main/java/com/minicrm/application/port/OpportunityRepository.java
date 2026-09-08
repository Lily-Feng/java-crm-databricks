package com.minicrm.application.port;

import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.OpportunityId;

import java.util.List;
import java.util.Optional;

public interface OpportunityRepository {

    Optional<Opportunity> findById(OpportunityId id);

    List<Opportunity> findByCustomerId(CustomerId customerId);

    /**
     * Backs {@code GET /dashboard/pipeline} (§3): pipeline totals are computed from every
     * open/closed opportunity, so the port needs an unfiltered read even though no other
     * endpoint requires one yet.
     */
    List<Opportunity> findAll();

    Opportunity save(Opportunity opportunity);
}
