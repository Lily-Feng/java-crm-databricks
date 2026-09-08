package com.minicrm.persistence.inmemory;

import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.opportunity.StaleVersionException;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;
import com.minicrm.domain.opportunity.Opportunity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryOpportunityRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final InMemoryOpportunityRepository repository = new InMemoryOpportunityRepository();

    private Opportunity save(Opportunity opportunity) {
        return repository.save(opportunity);
    }

    @Test
    void findByIdReturnsAnIsolatedCopyNotTheStoredInstance() {
        var opportunity = Opportunity.open(
                OpportunityId.newId(), CustomerId.newId(), "Acme renewal", Money.of("USD", 1_000_00),
                Probability.of(0.2), "alice", NOW);
        save(opportunity);

        var copy = repository.findById(opportunity.id()).orElseThrow();
        copy.changeStage(Stage.PROPOSAL, 0, NOW);

        // Mutating the caller's copy must not leak back into the repository's state
        // before save() is called — otherwise two callers loading "the same" opportunity
        // would really be sharing one mutable object, defeating the point of an
        // optimistic-concurrency check (see docs/learning/session-03.md).
        var storedAgain = repository.findById(opportunity.id()).orElseThrow();
        assertThat(storedAgain.stage()).isEqualTo(Stage.QUALIFIED);
        assertThat(storedAgain.version()).isZero();
    }

    @Test
    void saveRejectsAWriteBasedOnAStaleVersion() {
        var opportunity = Opportunity.open(
                OpportunityId.newId(), CustomerId.newId(), "Acme renewal", Money.of("USD", 1_000_00),
                Probability.of(0.2), "alice", NOW);
        save(opportunity);

        var firstUpdate = repository.findById(opportunity.id()).orElseThrow();
        firstUpdate.changeStage(Stage.PROPOSAL, 0, NOW);
        save(firstUpdate); // repository is now at version 1

        // A caller that loaded version 0 before the update above, and only now tries to
        // save its own version-1 result, is exactly what a genuine race produces —
        // reproduced directly here for a single-threaded assertion; the multi-threaded
        // version below shows the same rejection under a real concurrent race.
        var staleWriteBasedOnVersionZero = new Opportunity(
                opportunity.id(), opportunity.customerId(), "Acme renewal", Money.of("USD", 1_000_00),
                Stage.PROPOSAL, Probability.of(0.2), "alice", "", 1, NOW, NOW);

        assertThatThrownBy(() -> repository.save(staleWriteBasedOnVersionZero))
                .isInstanceOf(StaleVersionException.class);
    }

    @Test
    void concurrentSameVersionUpdatesProduceOneSuccessAndOneConflict() throws Exception {
        // §5.2: "Concurrent same-version updates must produce one success and one
        // conflict in the local integration test." Both threads load their own copy
        // (version 0) before either writes, forced by the barrier, so this reproduces a
        // genuine race deterministically rather than relying on scheduling luck.
        var opportunity = Opportunity.open(
                OpportunityId.newId(), CustomerId.newId(), "Acme renewal", Money.of("USD", 1_000_00),
                Probability.of(0.2), "alice", NOW);
        save(opportunity);

        var barrier = new CyclicBarrier(2);
        Callable<Opportunity> attempt = () -> {
            var copy = repository.findById(opportunity.id()).orElseThrow();
            copy.changeStage(Stage.PROPOSAL, 0, NOW);
            barrier.await();
            return repository.save(copy);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Opportunity>> futures = executor.invokeAll(List.of(attempt, attempt));

            int successes = 0;
            int conflicts = 0;
            for (Future<Opportunity> future : futures) {
                try {
                    future.get();
                    successes++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(StaleVersionException.class);
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(repository.findById(opportunity.id()).orElseThrow().version()).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
