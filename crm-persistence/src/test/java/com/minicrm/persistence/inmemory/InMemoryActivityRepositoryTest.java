package com.minicrm.persistence.inmemory;

import com.minicrm.domain.activity.Call;
import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryActivityRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final InMemoryActivityRepository repository = new InMemoryActivityRepository();
    private final CustomerId customerId = CustomerId.newId();

    @Test
    void firstSaveUnderAKeyWins() {
        var activity = new Call(ActivityId.newId(), customerId, "Check-in", NOW, "alice", 10);

        var stored = repository.save(activity, "key-1", "hash-1");

        assertThat(stored.activity()).isEqualTo(activity);
        assertThat(repository.findByCustomerId(customerId)).containsExactly(activity);
    }

    @Test
    void aSecondSaveUnderTheSameKeyReturnsTheFirstWithoutDuplicatingIt() {
        var first = new Call(ActivityId.newId(), customerId, "Check-in", NOW, "alice", 10);
        var second = new Call(ActivityId.newId(), customerId, "Check-in", NOW, "alice", 10);
        repository.save(first, "key-1", "hash-1");

        var winner = repository.save(second, "key-1", "hash-1");

        assertThat(winner.activity()).isEqualTo(first);
        assertThat(repository.findByCustomerId(customerId)).hasSize(1);
    }

    @Test
    void concurrentDuplicateCallsUnderTheSameKeyProduceExactlyOneStoredActivity() throws Exception {
        // §5.2: "Test concurrent duplicate calls." All N threads race to insert under the
        // same key at once; computeIfAbsent's per-key atomicity must leave exactly one
        // winner regardless of interleaving.
        var barrier = new CyclicBarrier(8);
        List<Callable<Call>> attempts = IntStream.range(0, 8)
                .<Callable<Call>>mapToObj(i -> () -> {
                    var candidate = new Call(ActivityId.newId(), customerId, "Check-in", NOW, "alice", 10);
                    barrier.await();
                    return (Call) repository.save(candidate, "key-1", "hash-1").activity();
                })
                .toList();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var results = executor.invokeAll(attempts);
            var winners = results.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).distinct().toList();

            assertThat(winners).hasSize(1);
            assertThat(repository.findByCustomerId(customerId)).hasSize(1);
        } finally {
            executor.shutdown();
        }
    }
}
