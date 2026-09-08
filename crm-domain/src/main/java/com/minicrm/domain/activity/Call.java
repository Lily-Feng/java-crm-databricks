package com.minicrm.domain.activity;

import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;

import java.time.Instant;
import java.util.Objects;

public record Call(
        ActivityId id,
        CustomerId customerId,
        String subject,
        Instant occurredAt,
        String createdBy,
        int durationMinutes) implements Activity {

    public Call {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes must not be negative: " + durationMinutes);
        }
    }
}
