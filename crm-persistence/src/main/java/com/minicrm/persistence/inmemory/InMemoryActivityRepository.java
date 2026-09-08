package com.minicrm.persistence.inmemory;

import com.minicrm.application.port.ActivityRepository;
import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.shared.CustomerId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * §5.2: idempotency is enforced with one atomic map operation
 * ({@link Map#computeIfAbsent}), the in-memory equivalent of "insert and enforce the key
 * in the same transaction." {@code computeIfAbsent}'s mapping function only runs if the
 * key is genuinely absent, so a concurrent duplicate call can never observe a half-written
 * entry or race past this check the way a separate get-then-put would.
 */
public final class InMemoryActivityRepository implements ActivityRepository {

    private record Key(CustomerId customerId, String idempotencyKey) {
    }

    private final Map<Key, StoredActivity> byIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<CustomerId, List<Activity>> byCustomerId = new ConcurrentHashMap<>();

    @Override
    public List<Activity> findByCustomerId(CustomerId customerId) {
        return List.copyOf(byCustomerId.getOrDefault(customerId, List.of()));
    }

    @Override
    public Optional<StoredActivity> findByIdempotencyKey(CustomerId customerId, String idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(new Key(customerId, idempotencyKey)));
    }

    @Override
    public StoredActivity save(Activity activity, String idempotencyKey, String requestHash) {
        var key = new Key(activity.customerId(), idempotencyKey);
        var candidate = new StoredActivity(activity, requestHash);

        var winner = byIdempotencyKey.computeIfAbsent(key, k -> candidate);
        if (winner == candidate) {
            byCustomerId.computeIfAbsent(activity.customerId(), c -> new CopyOnWriteArrayList<>()).add(activity);
        }
        return winner;
    }
}
