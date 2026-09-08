package com.minicrm.application.service;

import com.minicrm.application.port.IdempotencyConflictException;
import com.minicrm.application.service.support.FakeActivityRepository;
import com.minicrm.domain.activity.Call;
import com.minicrm.domain.shared.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ActivityService service = new ActivityService(new FakeActivityRepository(), clock);
    private final CustomerId customerId = CustomerId.newId();

    @Test
    void recordsANewCall() {
        var call = service.recordCall(customerId, "Renewal check-in", "alice", 15, "key-1");

        assertThat(call).isInstanceOf(Call.class);
        assertThat(service.findByCustomerId(customerId)).containsExactly(call);
    }

    @Test
    void replayingTheSameKeyAndContentReturnsTheOriginal() {
        var first = service.recordCall(customerId, "Renewal check-in", "alice", 15, "key-1");

        var replay = service.recordCall(customerId, "Renewal check-in", "alice", 15, "key-1");

        assertThat(replay).isEqualTo(first);
        assertThat(service.findByCustomerId(customerId)).hasSize(1);
    }

    @Test
    void reusingTheKeyWithDifferentContentIsAConflict() {
        service.recordCall(customerId, "Renewal check-in", "alice", 15, "key-1");

        assertThatThrownBy(() -> service.recordCall(customerId, "Different subject", "alice", 15, "key-1"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void theSameKeyIsIndependentPerCustomer() {
        var otherCustomer = CustomerId.newId();

        var first = service.recordCall(customerId, "Renewal check-in", "alice", 15, "key-1");
        var second = service.recordCall(otherCustomer, "Renewal check-in", "alice", 15, "key-1");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.findByCustomerId(customerId)).isEqualTo(List.of(first));
        assertThat(service.findByCustomerId(otherCustomer)).isEqualTo(List.of(second));
    }
}
