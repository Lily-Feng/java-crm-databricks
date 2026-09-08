package com.minicrm.persistence.lab;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * idea.md §6: "Have several threads modify {@code HashMap<CustomerId, Customer>}.
 * Observe incorrect behavior. Replace it with {@code ConcurrentHashMap}. Then discover
 * that get()/modify()/put() is still not automatically an atomic business transaction."
 *
 * <p>This lab deliberately does not try to reproduce raw {@code HashMap} structural
 * corruption under free-running concurrent {@code put} calls: that failure is real, but
 * its timing depends on JVM/hardware-specific resize behavior, and a hang or a wrong
 * size only sometimes appears — exactly the kind of nondeterminism design-spec §7 says
 * to explain rather than require on every CI run. Instead, both stages below use a
 * {@link CyclicBarrier} to force two threads to complete their read *before* either
 * writes, which reproduces the actual business-level lesson — a lost update from a
 * non-atomic get-then-put — 100% deterministically, every run. See
 * docs/learning/session-03.md for the reasoning and the raw-HashMap caveat.
 *
 * <p>Kept isolated from {@code crm-persistence}'s main sources (design-spec §2): nothing
 * here is production code, and {@link InMemoryOpportunityRepository} (main sources) is
 * the fixed version of this same lesson applied to a real business invariant.
 */
class NaiveMapConcurrencyLabTest {

    private static final String KEY = "customer-call-count";

    @Test
    void plainHashMapLosesAConcurrentIncrementUnderForcedInterleaving() throws Exception {
        Map<String, Integer> counters = new HashMap<>();
        counters.put(KEY, 0);

        int finalCount = incrementTwiceConcurrentlyByGetThenPut(counters);

        // The forbidden assumption: two independent get()-then-put() sequences on a plain
        // map are two atomic steps each, not one. Forcing both reads to happen before
        // either write reliably loses one increment.
        assertThat(finalCount).isEqualTo(1);
    }

    @Test
    void concurrentHashMapAloneDoesNotMakeGetThenPutAtomic() throws Exception {
        Map<String, Integer> counters = new ConcurrentHashMap<>();
        counters.put(KEY, 0);

        int finalCount = incrementTwiceConcurrentlyByGetThenPut(counters);

        // idea.md §6's key lesson: swapping the map implementation made individual get()
        // and put() calls thread-safe, but the two-step business transaction built out of
        // them is still not atomic. Same lost update as the plain HashMap above.
        assertThat(finalCount).isEqualTo(1);
    }

    @Test
    void computeIsAnAtomicBusinessTransactionAndLosesNoUpdates() throws Exception {
        Map<String, Integer> counters = new ConcurrentHashMap<>();
        counters.put(KEY, 0);

        var barrier = new CyclicBarrier(2);
        Runnable increment = () -> {
            await(barrier);
            counters.compute(KEY, (k, current) -> current + 1);
        };

        runConcurrently(increment, increment);

        // compute() holds the map's per-key atomicity for the whole read-modify-write
        // sequence, so both increments are always reflected, regardless of interleaving —
        // the fix idea.md §6 is driving at.
        assertThat(counters.get(KEY)).isEqualTo(2);
    }

    private static int incrementTwiceConcurrentlyByGetThenPut(Map<String, Integer> counters) throws Exception {
        var barrier = new CyclicBarrier(2);
        Runnable increment = () -> {
            int current = counters.get(KEY); // read
            await(barrier); // force both threads to have read before either writes
            counters.put(KEY, current + 1); // write: whichever runs last overwrites the other
        };

        runConcurrently(increment, increment);
        return counters.get(KEY);
    }

    private static void runConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.invokeAll(List.of(Executors.callable(first), Executors.callable(second)));
        } finally {
            executor.shutdown();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
