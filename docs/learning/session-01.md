# Session 01 — pure Java domain

## Concept

Model the domain (`Customer`, `Opportunity`, `Activity`, and shared value types) in plain
Java with no framework dependencies, and enforce that boundary automatically instead of
by convention (design-spec §4, idea.md §4).

Key decisions:

- Identifiers, `EmailAddress`, `Money` and `Probability` are records with compact
  constructors, so an invalid instance cannot exist even transiently.
- `Activity` is a sealed interface (`Call | Email | Meeting`) so a pattern-matching
  `switch` over it is exhaustive at compile time.
- `Money` is currency + integer minor units, never `double`; `plus` refuses to combine
  different currencies (design-spec §3).
- Stage-transition validity (`QUALIFIED → PROPOSAL → NEGOTIATION → CLOSED_WON`, any open
  stage → `CLOSED_LOST`, closed stages terminal) lives on the `Stage` enum itself, and
  `Opportunity.changeStage` checks `expectedVersion` *before* the transition, so a stale
  optimistic-concurrency conflict and an invalid-transition error stay distinguishable —
  this anticipates the `PATCH /opportunities/{id}/stage` contract in design-spec §3, even
  though there's no REST adapter yet.
- `Customer` does not embed `Contact`/`Activity`/`Opportunity` objects; they reference it
  by `CustomerId` instead, matching the normalized schema in design-spec §5.1. The 360
  view is an application-layer composition, arriving in a later milestone.

## Runnable command

```sh
./scripts/ci.sh
```

Runs `./mvnw -B verify` on the host if a working JDK is present, otherwise falls back to
`docker run maven:3.9-eclipse-temurin-25 mvn -B verify`.

## Observed result

```
Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Explanation

Two ArchUnit rules (`DomainHasNoFrameworkDependenciesTest`) assert that no class under
`com.minicrm.domain` depends on `org.springframework..` or `java.sql../javax.sql..`. On
the first run both rules failed — not because a violation was found, but because
ArchUnit 1.3 treats a rule that matches zero classes as a configuration error by default
(`failOnEmptyShould`). That default exists to catch rules with typo'd package names that
would otherwise silently pass forever. Since matching zero classes is exactly the
*correct* state today (the domain has no such classes to violate the rule), the fix is
`allowEmptyShould(true)`, not loosening the rule — the guard now stays meaningful as
`crm-persistence`/`crm-api` are added in later milestones and start actually importing
JDBC and Spring.

## Fix / comparison

Before the fix: `BUILD FAILURE` on two ArchUnit assertion errors that looked like real
violations but were a strictness setting. After: `BUILD SUCCESS`, and the rule will now
correctly fail the day a domain class imports Spring or JDBC.

## Remaining limitation

- This machine has no JDK or Maven installed natively, so `./mvnw` only works through the
  Docker fallback in `scripts/ci.sh`. `./scripts/ci.sh` output above ran through that
  fallback. This does not block the project (Docker is required from Milestone 5 anyway),
  but host-side test runs (design-spec §8: "Milestones 1–4 run Java tests/CLIs on the
  host") are, for this environment, container-side.
- No self-hosted GitHub Actions runner is registered yet, so `.github/workflows/ci.yml`
  is committed but unexercised as a live workflow; `scripts/ci.sh` is the verified
  equivalent for now.
- `generics`, `collections`, `Optional`, and `Stream` review is deferred to Milestone 2/3
  by design (repository ports don't exist yet).
