# Session 03 — in-memory CRM and analytics

## Concept

Give every one of the eight business operations (design-spec §3) an in-memory
implementation — application services in `crm-application`, `ConcurrentHashMap`-backed
adapters in the new `crm-persistence` module — and implement the five idea.md §11 CRM
analytics twice, once as loops and once as Streams, checked against each other rather
than trusted to be equivalent by inspection.

## The naive-vs-atomic map exercise (idea.md §6)

idea.md §6's lesson: "Have several threads modify `HashMap<CustomerId, Customer>`.
Observe incorrect behavior. Replace it with `ConcurrentHashMap`. Then discover that
`get()`/`modify()`/`put()` is still not automatically an atomic business transaction."

`NaiveMapConcurrencyLabTest` reproduces this in three stages, all forced through a
`CyclicBarrier` so both threads complete their read before either writes:

1. Plain `HashMap`, `get`-then-`put`: loses one of two concurrent increments (asserted:
   final count is 1, not 2).
2. `ConcurrentHashMap`, same `get`-then-`put`: **still** loses the increment — the map
   type change made individual calls thread-safe, not the two-step sequence built from
   them.
3. `ConcurrentHashMap.compute`: no lost update — the read-modify-write is now one atomic
   map operation.

**A deliberate choice**: this lab does not try to reproduce raw `HashMap` internal
structural corruption (lost entries, `ConcurrentModificationException`, or a resize-race
hang) under free-running concurrent `put` calls with distinct keys. That failure is real,
but its timing depends on JVM/hardware-specific resize behavior — sometimes it doesn't
manifest at all in a given run, and in the worst case a resize race can hang a thread.
Given design-spec §7's explicit instruction for JMM-adjacent labs — "explain the
forbidden assumption and repeat in a bounded harness rather than require an unsafe
outcome on every CI run" — and the stronger requirement that "the default build must
remain green and bounded" (§2), a hang-risking, sometimes-silent reproduction was the
wrong trade for this milestone. The barrier-forced lost-update version demonstrates the
*same underlying lesson* ("this compound operation is not atomic") 100% deterministically
instead, verified over 5 repeated runs with zero flakiness.

This same atomic-`compute`/`computeIfAbsent` pattern is then used for real, not just in
the isolated lab:

- `InMemoryOpportunityRepository.save` uses `ConcurrentHashMap.compute` to enforce
  optimistic-concurrency stage updates (mirrors the Postgres `WHERE version = ?` pattern
  from §5.2) — verified by a genuine multi-threaded test
  (`concurrentSameVersionUpdatesProduceOneSuccessAndOneConflict`) using the same
  barrier-forcing technique, confirmed deterministic over 5 runs.
- `InMemoryActivityRepository.save` uses `computeIfAbsent` for idempotency-key
  insert-if-absent (§5.2) — verified by an 8-thread race under one key
  (`concurrentDuplicateCallsUnderTheSameKeyProduceExactlyOneStoredActivity`).

A second, related bug this milestone caught and fixed before it could hide in
"production" code: `InMemoryOpportunityRepository.findById` returns a **defensive copy**,
not the stored `Opportunity` reference. `Opportunity` is a mutable aggregate; if two
callers both received the same shared instance, one caller mutating its local copy via
`changeStage` would corrupt the other caller's in-flight state *before either called
`save`*, defeating the optimistic-concurrency check entirely (both would appear to agree
on a version that was silently already stale). `findByIdReturnsAnIsolatedCopyNotTheStoredInstance`
asserts this directly.

## Loops vs Streams (idea.md §11)

`OpportunityAnalyticsLoops`/`OpportunityAnalyticsStreams` (revenue by owner, average
opportunity size, top-N opportunities, conversion by stage) and
`ActivityAnalyticsLoops`/`ActivityAnalyticsStreams` (activities by customer) are asserted
equal on the same fixture data. One subtlety surfaced by the equivalence test itself:
`Collectors.averagingLong` computes a `double` average, which doesn't match the loop
version's integer division (`sum / count`) at the boundary — e.g. 7/2 is `3` by integer
division but `3.5` rounding to `4` via `Math.round`. The Streams version was changed to
use `summingLong` + `counting` + explicit integer division instead, so both
implementations compute the literal same aggregate rather than two different
"reasonable" definitions of average that happen to usually agree.

## Runnable command

```sh
./scripts/ci.sh
```

## Observed result

```
crm-domain:      Tests run: 51
crm-application: Tests run: 31
crm-persistence: Tests run: 15
BUILD SUCCESS
```

Concurrency-sensitive tests (`NaiveMapConcurrencyLabTest`,
`InMemoryOpportunityRepositoryTest`, `InMemoryActivityRepositoryTest`) were additionally
run 5 times in a row with zero failures, to check the barrier-based determinism claim
above rather than assert it from reading the code.

## Remaining limitation

- `crm-persistence` currently only holds in-memory adapters; the shared Postgres/Lakebase
  repository implementation described in design-spec §4/§5.3 arrives at Milestone 4.
- No REST or MCP adapter yet, so the eight operations are only reachable from tests —
  design-spec §13 places REST at Milestone 5 and MCP at Milestone 12, both intentionally
  after this in-memory foundation.
- `InMemoryCustomerRepository` does not get the same defensive-copy/optimistic-concurrency
  treatment as `InMemoryOpportunityRepository`: there is no versioned update endpoint for
  customers yet (only create/read), so there is no compound business transaction to
  protect. Revisit if/when a customer update endpoint is added.
