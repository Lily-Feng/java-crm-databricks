package com.minicrm.application.analytics;

import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.activity.Call;
import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityAnalyticsEquivalenceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void activitiesByCustomerMatches() {
        var customerA = CustomerId.newId();
        var customerB = CustomerId.newId();
        List<Activity> activities = List.of(
                new Call(ActivityId.newId(), customerA, "Check-in", NOW, "alice", 10),
                new Call(ActivityId.newId(), customerA, "Follow-up", NOW, "alice", 5),
                new Call(ActivityId.newId(), customerB, "Intro", NOW, "bob", 20));

        assertThat(ActivityAnalyticsLoops.activitiesByCustomer(activities))
                .isEqualTo(ActivityAnalyticsStreams.activitiesByCustomer(activities))
                .containsEntry(customerA, 2L)
                .containsEntry(customerB, 1L);
    }
}
