package com.minicrm.application.port;

import com.minicrm.domain.shared.CustomerId;

/**
 * §5.2: "same key with different content → 409". Thrown when a repeated
 * {@code Idempotency-Key} arrives with a request that hashes differently from the one
 * originally stored under that key.
 */
public final class IdempotencyConflictException extends RuntimeException {

    private final CustomerId customerId;
    private final String idempotencyKey;

    public IdempotencyConflictException(CustomerId customerId, String idempotencyKey) {
        super("Idempotency key %s for customer %s was reused with different request content"
                .formatted(idempotencyKey, customerId));
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
