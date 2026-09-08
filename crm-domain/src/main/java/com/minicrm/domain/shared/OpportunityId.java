package com.minicrm.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record OpportunityId(UUID value) {

    public OpportunityId {
        Objects.requireNonNull(value, "OpportunityId value must not be null");
    }

    public static OpportunityId newId() {
        return new OpportunityId(UUID.randomUUID());
    }

    public static OpportunityId of(String value) {
        return new OpportunityId(UUID.fromString(value));
    }
}
