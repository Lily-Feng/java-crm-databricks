package com.minicrm.domain.opportunity;

import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;

import java.time.Instant;
import java.util.Objects;

/**
 * Mutable aggregate carrying an optimistic-concurrency version (§3). Stage validation
 * belongs here, not in the persistence or REST layers, so every adapter shares one rule.
 */
public final class Opportunity {

    private final OpportunityId id;
    private final CustomerId customerId;
    private String name;
    private Money amount;
    private Stage stage;
    private Probability probability;
    private String owner;
    private String notes;
    private int version;
    private final Instant createdAt;
    private Instant updatedAt;

    public Opportunity(
            OpportunityId id,
            CustomerId customerId,
            String name,
            Money amount,
            Stage stage,
            Probability probability,
            String owner,
            String notes,
            int version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.probability = Objects.requireNonNull(probability, "probability must not be null");
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.notes = notes == null ? "" : notes;
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative: " + version);
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Opportunity open(
            OpportunityId id,
            CustomerId customerId,
            String name,
            Money amount,
            Probability probability,
            String owner,
            Instant now) {
        return new Opportunity(id, customerId, name, amount, Stage.QUALIFIED, probability, owner, "", 0, now, now);
    }

    /**
     * §3, §5.2: mirrors the optimistic-locking SQL update — reject a stale caller before
     * checking whether the transition itself is legal, so the two 409 causes stay distinguishable.
     */
    public void changeStage(Stage target, int expectedVersion, Instant now) {
        Objects.requireNonNull(target, "target must not be null");
        if (expectedVersion != version) {
            throw new StaleVersionException(id, expectedVersion, version);
        }
        if (!stage.canTransitionTo(target)) {
            throw new InvalidStageTransitionException(stage, target);
        }
        this.stage = target;
        this.version = version + 1;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public OpportunityId id() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public String name() {
        return name;
    }

    public Money amount() {
        return amount;
    }

    public Stage stage() {
        return stage;
    }

    public Probability probability() {
        return probability;
    }

    public String owner() {
        return owner;
    }

    public String notes() {
        return notes;
    }

    public int version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
