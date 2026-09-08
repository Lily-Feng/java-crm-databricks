package com.minicrm.persistence.inmemory;

import com.minicrm.application.port.OpportunityRepository;
import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.opportunity.StaleVersionException;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.OpportunityId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mirrors the Postgres optimistic-locking statement in §5.2
 * ({@code UPDATE ... WHERE opportunity_id = ? AND version = ?}) without a database: every
 * read hands the caller a private copy (so two callers never mutate the same
 * {@link Opportunity} instance concurrently — see docs/learning/session-03.md for what
 * happens when a repository skips this), and {@link #save} atomically accepts a write
 * only if the stored version still matches what the caller's update was based on.
 *
 * <p>This is the "atomic operation" side of idea.md §6's naive-map-then-atomic-map
 * progression, applied to a real business invariant rather than an isolated lab.
 */
public final class InMemoryOpportunityRepository implements OpportunityRepository {

    private final Map<OpportunityId, Opportunity> opportunities = new ConcurrentHashMap<>();

    @Override
    public Optional<Opportunity> findById(OpportunityId id) {
        return Optional.ofNullable(opportunities.get(id)).map(InMemoryOpportunityRepository::copyOf);
    }

    @Override
    public List<Opportunity> findByCustomerId(CustomerId customerId) {
        return opportunities.values().stream()
                .filter(o -> o.customerId().equals(customerId))
                .map(InMemoryOpportunityRepository::copyOf)
                .toList();
    }

    @Override
    public List<Opportunity> findAll() {
        return opportunities.values().stream().map(InMemoryOpportunityRepository::copyOf).toList();
    }

    @Override
    public Opportunity save(Opportunity opportunity) {
        var expectedPriorVersion = opportunity.version() - 1;
        var toStore = copyOf(opportunity);
        var result = new AtomicReference<Opportunity>();

        opportunities.compute(opportunity.id(), (id, current) -> {
            if (current != null && current.version() != expectedPriorVersion) {
                throw new StaleVersionException(id, expectedPriorVersion, current.version());
            }
            result.set(toStore);
            return toStore;
        });

        return copyOf(result.get());
    }

    private static Opportunity copyOf(Opportunity source) {
        return new Opportunity(
                source.id(), source.customerId(), source.name(), source.amount(), source.stage(),
                source.probability(), source.owner(), source.notes(), source.version(),
                source.createdAt(), source.updatedAt());
    }
}
