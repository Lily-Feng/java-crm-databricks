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

    Activity save(Activity activity, String idempotencyKey, String requestHash);

    record StoredActivity(Activity activity, String requestHash) {
    }
}
