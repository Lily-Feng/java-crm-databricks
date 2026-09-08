package com.minicrm.application.service.support;

import com.minicrm.application.port.ActivityRepository;
import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.shared.CustomerId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single-threaded fake: real atomic insert-if-absent semantics under concurrency are
 * verified against crm-persistence's InMemoryActivityRepository, not here.
 */
public final class FakeActivityRepository implements ActivityRepository {

    private record Key(CustomerId customerId, String idempotencyKey) {
    }

    private final List<Activity> all = new ArrayList<>();
    private final Map<Key, StoredActivity> byIdempotencyKey = new LinkedHashMap<>();

    @Override
    public List<Activity> findByCustomerId(CustomerId customerId) {
        return all.stream().filter(a -> a.customerId().equals(customerId)).toList();
    }

    @Override
    public Optional<StoredActivity> findByIdempotencyKey(CustomerId customerId, String idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(new Key(customerId, idempotencyKey)));
    }

    @Override
    public StoredActivity save(Activity activity, String idempotencyKey, String requestHash) {
        var key = new Key(activity.customerId(), idempotencyKey);
        var existing = byIdempotencyKey.get(key);
        if (existing != null) {
            return existing;
        }
        var stored = new StoredActivity(activity, requestHash);
        byIdempotencyKey.put(key, stored);
        all.add(activity);
        return stored;
    }
}
