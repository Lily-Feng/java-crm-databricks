package com.minicrm.domain.activity;

import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Meeting(
        ActivityId id,
        CustomerId customerId,
        String subject,
        Instant occurredAt,
        String createdBy,
        List<String> attendees) implements Activity {

    public Meeting {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        attendees = List.copyOf(Objects.requireNonNullElse(attendees, List.of()));
    }
}
