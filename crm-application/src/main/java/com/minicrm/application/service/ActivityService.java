package com.minicrm.application.service;

import com.minicrm.application.port.ActivityRepository;
import com.minicrm.application.port.IdempotencyConflictException;
import com.minicrm.application.util.RequestHash;
import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.activity.Call;
import com.minicrm.domain.activity.Email;
import com.minicrm.domain.activity.Meeting;
import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Backs {@code POST /customers/{id}/activities} (§3), which requires a client-generated
 * {@code Idempotency-Key}. §5.2: same key + same request content returns the original
 * result; same key + different content is a conflict.
 */
public final class ActivityService {

    private final ActivityRepository activities;
    private final Clock clock;

    public ActivityService(ActivityRepository activities, Clock clock) {
        this.activities = Objects.requireNonNull(activities, "activities must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Activity recordCall(
            CustomerId customerId, String subject, String createdBy, int durationMinutes, String idempotencyKey) {
        var requestContent = String.join("|", "Call", customerId.toString(), subject, createdBy,
                Integer.toString(durationMinutes));
        return recordActivity(customerId, idempotencyKey, requestContent,
                id -> new Call(id, customerId, subject, clock.instant(), createdBy, durationMinutes));
    }

    public Activity recordEmail(
            CustomerId customerId, String subject, String createdBy, String bodyPreview, String idempotencyKey) {
        var requestContent = String.join("|", "Email", customerId.toString(), subject, createdBy, bodyPreview);
        return recordActivity(customerId, idempotencyKey, requestContent,
                id -> new Email(id, customerId, subject, clock.instant(), createdBy, bodyPreview));
    }

    public Activity recordMeeting(
            CustomerId customerId, String subject, String createdBy, List<String> attendees, String idempotencyKey) {
        var requestContent =
                String.join("|", "Meeting", customerId.toString(), subject, createdBy, String.join(",", attendees));
        return recordActivity(customerId, idempotencyKey, requestContent,
                id -> new Meeting(id, customerId, subject, clock.instant(), createdBy, attendees));
    }

    public List<Activity> findByCustomerId(CustomerId customerId) {
        return activities.findByCustomerId(customerId);
    }

    /**
     * No read-then-write here: {@link ActivityRepository#save} does the atomic
     * insert-if-absent, and this method only compares the returned winner's hash against
     * the caller's own — the same check whether this call won, replayed an identical
     * prior call, or lost a race to a genuinely different one (§5.2).
     */
    private Activity recordActivity(
            CustomerId customerId, String idempotencyKey, String requestContent,
            java.util.function.Function<ActivityId, Activity> factory) {
        var requestHash = RequestHash.sha256(requestContent);
        var activity = factory.apply(ActivityId.newId());

        var winner = activities.save(activity, idempotencyKey, requestHash);
        if (!winner.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(customerId, idempotencyKey);
        }
        return winner.activity();
    }
}
