package com.minicrm.application.service;

import com.minicrm.domain.shared.OpportunityId;

/** Maps to REST 404 (§3), distinct from a stale-version 409. */
public final class OpportunityNotFoundException extends RuntimeException {

    private final OpportunityId opportunityId;

    public OpportunityNotFoundException(OpportunityId opportunityId) {
        super("No opportunity with id " + opportunityId);
        this.opportunityId = opportunityId;
    }

    public OpportunityId opportunityId() {
        return opportunityId;
    }
}
