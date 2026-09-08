package com.minicrm.application.port;

import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.shared.CustomerId;

import java.util.List;
import java.util.Optional;

/**
 * §5.2: idempotency is scoped to (customerId, idempotencyKey). {@code requestHash} lets
 * an application service tell "same key, same request" (return the original) apart from
 * "same key, different content" (409) without re-deserializing the stored activity.
 */
public interface ActivityRepository {

    List<Activity> findByCustomerId(CustomerId customerId);

    Optional<StoredActivity> findByIdempotencyKey(CustomerId customerId, String idempotencyKey);

    /**
     * Atomically inserts {@code activity} under {@code (customerId, idempotencyKey)} if
     * and only if no record exists there yet, then returns whichever
     * {@link StoredActivity} now occupies that key — the caller's own insert, or a
     * concurrent duplicate call's, whichever the adapter's underlying map serialized
     * first (§5.2: "Insert and enforce the key in the same transaction"). The caller
     * decides what the returned hash means by comparing it against its own
     * {@code requestHash}; this method never throws for a hash mismatch itself.
     */
    StoredActivity save(Activity activity, String idempotencyKey, String requestHash);

    record StoredActivity(Activity activity, String requestHash) {
    }
}
