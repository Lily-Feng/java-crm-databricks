# Session 02 — repository ports and generics

## Concept

Define the repository ports (`CustomerRepository`, `ContactRepository`,
`OpportunityRepository`, `ActivityRepository`) that application services and every
persistence adapter will share (design-spec §4), and use them as the vehicle for a real
PECS example rather than a textbook one (idea.md §5).

Key decisions:

- `crm-application` depends only on `crm-domain` — no adapter (`crm-persistence`,
  `crm-databricks`, `crm-api`, `crm-mcp`) exists yet, but an ArchUnit rule already
  forbids `com.minicrm.application` from depending on those packages, so the first
  adapter that reaches back into application code fails the build immediately.
- `CustomerImport.importInto(Iterable<? extends Customer> source, Collection<? super
  Customer> destination)` is idea.md §5's own example, implemented and tested: `source`
  is typed `? extends Customer` (Producer Extends — it only ever hands out `Customer`s),
  `destination` is typed `? super Customer` (Consumer Super — it only ever accepts
  `Customer`s, so `Collection<Object>` is a legal destination). `CustomerRepository`'s
  default `saveAll` method is the same bound (`Collection<? extends Customer>`) applied
  to something the port will actually be called with.
- `CustomerQuery.limit` is bounded to `[1, 100]` in the record's compact constructor
  (design-spec §3: "Bounded search results; pagination/limit documented") so the bound
  is enforced once, in the port's own value type, not repeated in every adapter.
- `ActivityRepository` carries a `requestHash` alongside the stored `Activity` so a later
  application service can distinguish "same idempotency key, same request" (return the
  original) from "same key, different content" (409) without re-deriving a hash from the
  stored domain object (design-spec §5.2).

## Runnable command

```sh
./scripts/ci.sh
```

## Observed result

```
crm-domain:      Tests run: 51, Failures: 0
crm-application: Tests run: 10, Failures: 0
BUILD SUCCESS
```

## Explanation

No naive-then-fixed progression this session — repository ports are interfaces with one
default method, so there is nothing to break yet. The interesting decision was scope: it
was tempting to also sketch `crm-persistence`'s in-memory implementation here, since it's
a natural next step. That's deliberately Milestone 3's job (design-spec §13 table: "In-
memory CRM + analytics"), where the naive-`HashMap`-then-broken-then-`ConcurrentHashMap`
progression from idea.md §6 actually needs somewhere to live. Building it now would have
meant either duplicating that lesson early or building it without the concurrency test
that makes it worth building.

## Fix / comparison

N/A this session — see above.

## Remaining limitation

- `OpportunityRepository.findAll()` and `ActivityRepository`'s idempotency methods have
  no implementation or integration test yet; they're specified now because the ports
  milestone is where the full surface belongs, but they're only exercised once an
  in-memory adapter exists (Milestone 3) and once Postgres enforces the unique
  constraint for real (Milestone 4).
- Still no local JDK; verified via the `maven:3.9-eclipse-temurin-25` Docker fallback in
  `scripts/ci.sh`, same as session 01.
