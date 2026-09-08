package com.minicrm.application.service.support;

import com.minicrm.application.port.OpportunityRepository;
import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.OpportunityId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FakeOpportunityRepository implements OpportunityRepository {

    private final Map<OpportunityId, Opportunity> opportunities = new LinkedHashMap<>();

    @Override
    public Optional<Opportunity> findById(OpportunityId id) {
        return Optional.ofNullable(opportunities.get(id));
    }

    @Override
    public List<Opportunity> findByCustomerId(CustomerId customerId) {
        return opportunities.values().stream().filter(o -> o.customerId().equals(customerId)).toList();
    }

    @Override
    public List<Opportunity> findAll() {
        return List.copyOf(opportunities.values());
    }

    @Override
    public Opportunity save(Opportunity opportunity) {
        opportunities.put(opportunity.id(), opportunity);
        return opportunity;
    }
}
