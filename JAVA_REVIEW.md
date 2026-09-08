# Java review matrix

Tracks idea.md §20 against MiniCRM code, a test/lab, and the lesson learned. Updated at
every milestone (spec §2). An item is only checked once there is runnable evidence —
"planned" is not evidence.

Legend: ✅ covered · ⏳ planned for a later milestone · — not required by scope

## Classic curriculum (idea.md §4–5, §9–10)

| Topic | Status | Evidence | Lesson |
|---|---|---|---|
| primitives / wrappers | ⏳ | — | Deferred; no primitive-heavy code has needed review yet. |
| String / immutability | ✅ | [`EmailAddress`](crm-domain/src/main/java/com/minicrm/domain/shared/EmailAddress.java), [`Money`](crm-domain/src/main/java/com/minicrm/domain/shared/Money.java) | Value types are records over immutable fields; no setters anywhere in `crm-domain`. |
| equals/hashCode | ✅ | Records ([`CustomerId`](crm-domain/src/main/java/com/minicrm/domain/shared/CustomerId.java) etc.) | Records generate value-based `equals`/`hashCode`; verified by [`IdentifierTest.newIdGeneratesDistinctValues`](crm-domain/src/test/java/com/minicrm/domain/shared/IdentifierTest.java). |
| records | ✅ | All value types under [`shared/`](crm-domain/src/main/java/com/minicrm/domain/shared/), [`Call`](crm-domain/src/main/java/com/minicrm/domain/activity/Call.java)/[`Email`](crm-domain/src/main/java/com/minicrm/domain/activity/Email.java)/[`Meeting`](crm-domain/src/main/java/com/minicrm/domain/activity/Meeting.java) | Compact constructors enforce invariants (non-null, `@`, non-negative, [0,1] range) at construction time, not later. |
| sealed classes | ✅ | [`Activity`](crm-domain/src/main/java/com/minicrm/domain/activity/Activity.java) permits `Call`, `Email`, `Meeting` | The compiler rejects a non-exhaustive `switch` if a fourth subtype is added without updating `summary()`. |
| inheritance | — | — | Domain favors composition/sealed hierarchies over class inheritance; revisit if a genuine "is-a" need appears. |
| composition | ✅ | [`Customer`](crm-domain/src/main/java/com/minicrm/domain/customer/Customer.java) owns tags; `Opportunity`/`Contact`/`Activity` reference `CustomerId` rather than embedding | Matches the normalized schema in design-spec §5.1 — 360 aggregation happens at the application layer, not by nesting. |
| interfaces | ✅ | [`Activity`](crm-domain/src/main/java/com/minicrm/domain/activity/Activity.java) | Sealed interface as a closed, exhaustively-switchable type. |
| abstract types | — | — | No abstract classes needed yet at this module's size. |
| polymorphism | ✅ | `Activity.summary()` pattern-matching switch | Dispatch by pattern rather than by overriding, per idea.md §4's `switch` example. |
| static/final | ✅ | `Stage.OPEN_STAGES`, all domain fields `final` except mutable-aggregate state | `Opportunity`/`Customer` intentionally mix `final` identity fields with mutable state fields to model an aggregate with a version. |
| initialization | ✅ | Record compact constructors; [`Opportunity`](crm-domain/src/main/java/com/minicrm/domain/opportunity/Opportunity.java) constructor validation | Invariants enforced before an instance can exist. |
| exceptions | ✅ | [`InvalidStageTransitionException`](crm-domain/src/main/java/com/minicrm/domain/opportunity/InvalidStageTransitionException.java), [`StaleVersionException`](crm-domain/src/main/java/com/minicrm/domain/opportunity/StaleVersionException.java) | Two distinct unchecked exception types so a stale-version 409 and an invalid-transition 409 stay distinguishable at the REST adapter (Milestone 5). |
| generics | ✅ | [`CustomerImport.importInto`](crm-application/src/main/java/com/minicrm/application/util/CustomerImport.java), [`CustomerRepository.saveAll`](crm-application/src/main/java/com/minicrm/application/port/CustomerRepository.java) | idea.md §5's PECS example verbatim: `Iterable<? extends Customer>` (producer) into `Collection<? super Customer>` (consumer). Type erasure and bounded wildcards are exercised, not just quoted. |
| collections | ✅ | [`CustomerOrdering`](crm-application/src/main/java/com/minicrm/application/customer/CustomerOrdering.java), [`InMemoryCustomerRepository`](crm-persistence/src/main/java/com/minicrm/persistence/inmemory/InMemoryCustomerRepository.java) | `Comparator.comparing(...).thenComparing(...)` for deterministic, case-insensitive search ordering; `ConcurrentHashMap`-backed in-memory adapters ([`InMemoryOpportunityRepository`](crm-persistence/src/main/java/com/minicrm/persistence/inmemory/InMemoryOpportunityRepository.java), [`InMemoryActivityRepository`](crm-persistence/src/main/java/com/minicrm/persistence/inmemory/InMemoryActivityRepository.java)) for the "naive vs atomic map" section below. |
| iterators | ✅ | Enhanced-`for` loops throughout [`OpportunityAnalyticsLoops`](crm-application/src/main/java/com/minicrm/application/analytics/OpportunityAnalyticsLoops.java) | Reviewed as part of the loops-vs-Streams comparison rather than in isolation. |
| nested classes | — | — | Not yet needed. |
| annotations | ⏳ | — | Arrives with Spring (Milestone 5) and JUnit (already implicitly reviewed via `@Test`/`@ParameterizedTest`). |
| reflection | ⏳ | — | ArchUnit already uses reflection internally ([`DomainHasNoFrameworkDependenciesTest`](crm-domain/src/test/java/com/minicrm/architecture/DomainHasNoFrameworkDependenciesTest.java)); explicit reflection lab is a §4 I/O/NIO-class focused lab, deferred. |
| enums | ✅ | [`Stage`](crm-domain/src/main/java/com/minicrm/domain/opportunity/Stage.java) | Enum method (`canTransitionTo`) encodes the allowed stage graph instead of scattering `if` chains across callers. |
| I/O | ⏳ | — | Focused lab, deferred (design-spec §2). |
| NIO | ⏳ | — | Focused lab, deferred. |
| serialization concepts | ⏳ | — | Activity persistence serialization is Milestone 4 (design-spec §5.1). |

## Modern Java (idea.md §4)

| Topic | Status | Evidence | Lesson |
|---|---|---|---|
| lambdas | — | — | Arrives naturally with Streams (Milestone 3). |
| functional interfaces | — | — | Milestone 3. |
| Stream API | ✅ | [`OpportunityAnalyticsStreams`](crm-application/src/main/java/com/minicrm/application/analytics/OpportunityAnalyticsStreams.java), [`ActivityAnalyticsStreams`](crm-application/src/main/java/com/minicrm/application/analytics/ActivityAnalyticsStreams.java) | `map`/`filter`/`sorted`/`limit`/`groupingBy`/`counting`/`reducing`/`toMap`, each cross-checked byte-for-byte against a loop implementation on the same fixture data ([`OpportunityAnalyticsEquivalenceTest`](crm-application/src/test/java/com/minicrm/application/analytics/OpportunityAnalyticsEquivalenceTest.java)). |
| Optional | ✅ | [`CustomerRepository.findById`](crm-application/src/main/java/com/minicrm/application/port/CustomerRepository.java), [`CustomerQuery.search`](crm-application/src/main/java/com/minicrm/application/port/CustomerQuery.java) | `Optional<T>` as a port return type (absent vs. present) and as a record field (`Optional<String>` search term) rather than `null`. |
| var | ✅ | Test bodies throughout `crm-domain/src/test` | Used where the right-hand side already makes the type obvious. |
| text blocks | — | — | No multi-line string literal needed yet. |
| pattern matching / switch expressions | ✅ | [`Activity.summary()`](crm-domain/src/main/java/com/minicrm/domain/activity/Activity.java), [`Stage.canTransitionTo`](crm-domain/src/main/java/com/minicrm/domain/opportunity/Stage.java) | Exhaustive `switch` over a sealed interface and over an enum, both without a `default` branch. |
| modules (JPMS) | — | — | Not adopted; Maven multi-module boundaries plus ArchUnit are the chosen dependency-direction enforcement (design-spec §4). |
| immutable data | ✅ | All `shared` value types, `Call`/`Email`/`Meeting` | Compact constructors + defensive `List.copyOf` in [`Meeting`](crm-domain/src/main/java/com/minicrm/domain/activity/Meeting.java), verified by `meetingAttendeesAreDefensivelyCopiedAndImmutable`. |

## Concurrency (idea.md §9–10) — deferred to Milestones 6–9, 13

| Topic | Status |
|---|---|
| Thread / Runnable / Callable / Future | ⏳ Milestone 6 |
| ExecutorService / thread pools / shutdown / interruption | ⏳ Milestone 6 |
| CompletableFuture | ⏳ Milestone 7 |
| virtual threads | ⏳ Milestone 8 |
| structured concurrency (preview) | ⏳ Milestone 13 |
| ScopedValue / context propagation | ⏳ Milestone 11 lab |
| synchronized / volatile / locks / atomics | ⏳ Milestone 9 (failure labs 01–10) |
| concurrent collections (`ConcurrentHashMap`, `CopyOnWriteArrayList`) | ✅ [`NaiveMapConcurrencyLabTest`](crm-persistence/src/test/java/com/minicrm/persistence/lab/NaiveMapConcurrencyLabTest.java), [`InMemoryOpportunityRepository`](crm-persistence/src/main/java/com/minicrm/persistence/inmemory/InMemoryOpportunityRepository.java), [`InMemoryActivityRepository`](crm-persistence/src/main/java/com/minicrm/persistence/inmemory/InMemoryActivityRepository.java) — idea.md §6's naive-`HashMap`-then-`ConcurrentHashMap`-then-atomic-`compute` progression, forced deterministically with a `CyclicBarrier`; the same `compute`/`computeIfAbsent` atomicity is then applied for real to optimistic-concurrency stage updates and activity idempotency. |
| JMM (happens-before, visibility, CAS) | ⏳ Milestone 9 (full treatment); this milestone's lab only exercises the lost-update symptom, not visibility/ordering directly |

## JVM (idea.md §16)

| Topic | Status |
|---|---|
| JVM memory / GC | ⏳ Milestone 11 |
| class loading | ⏳ Focused lab, deferred |
| JFR / jcmd / profiling | ⏳ Milestone 11 |

---

See `docs/learning/session-NN.md` for each milestone's runnable command, observed
result, and remaining limitations.
