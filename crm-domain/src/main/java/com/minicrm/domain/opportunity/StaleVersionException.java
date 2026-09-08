package com.minicrm.domain.opportunity;

import com.minicrm.domain.shared.OpportunityId;

/**
 * Optimistic-concurrency conflict (§3, §5.2): a PATCH arrived with an
 * {@code expectedVersion} that no longer matches the aggregate's current version.
 */
public final class StaleVersionException extends RuntimeException {

    private final OpportunityId opportunityId;
    private final int expectedVersion;
    private final int actualVersion;

    public StaleVersionException(OpportunityId opportunityId, int expectedVersion, int actualVersion) {
        super("Stale version for opportunity %s: expected %d but was %d"
                .formatted(opportunityId, expectedVersion, actualVersion));
        this.opportunityId = opportunityId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public OpportunityId opportunityId() {
        return opportunityId;
    }

    public int expectedVersion() {
        return expectedVersion;
    }

    public int actualVersion() {
        return actualVersion;
    }
}
