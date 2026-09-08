package com.minicrm.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record ActivityId(UUID value) {

    public ActivityId {
        Objects.requireNonNull(value, "ActivityId value must not be null");
    }

    public static ActivityId newId() {
        return new ActivityId(UUID.randomUUID());
    }

    public static ActivityId of(String value) {
        return new ActivityId(UUID.fromString(value));
    }
}
