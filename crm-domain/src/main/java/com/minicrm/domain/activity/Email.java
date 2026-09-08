package com.minicrm.domain.activity;

import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;

import java.time.Instant;
import java.util.Objects;

public record Email(
        ActivityId id,
        CustomerId customerId,
        String subject,
        Instant occurredAt,
        String createdBy,
        String bodyPreview) implements Activity {

    public Email {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(bodyPreview, "bodyPreview must not be null");
    }
}
