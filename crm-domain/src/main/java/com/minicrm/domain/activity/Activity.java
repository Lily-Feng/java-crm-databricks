package com.minicrm.domain.activity;

import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;

import java.time.Instant;

/**
 * §3: sealed Activity hierarchy with pattern-matching switch over its subtypes.
 */
public sealed interface Activity permits Call, Email, Meeting {

    ActivityId id();

    CustomerId customerId();

    Instant occurredAt();

    String createdBy();

    /**
     * Demonstrates exhaustive pattern-matching switch over a sealed hierarchy (§3/idea.md §4):
     * the compiler rejects this switch if a fourth Activity subtype is ever added without a case.
     */
    default String summary() {
        return switch (this) {
            case Call c -> "Call: %s (%d min)".formatted(c.subject(), c.durationMinutes());
            case Email e -> "Email: %s".formatted(e.subject());
            case Meeting m -> "Meeting: %s (%d attendees)".formatted(m.subject(), m.attendees().size());
        };
    }
}
