package com.minicrm.application.service;

import com.minicrm.domain.shared.CustomerId;

/** Maps to REST 404 (§3). */
public final class CustomerNotFoundException extends RuntimeException {

    private final CustomerId customerId;

    public CustomerNotFoundException(CustomerId customerId) {
        super("No customer with id " + customerId);
        this.customerId = customerId;
    }

    public CustomerId customerId() {
        return customerId;
    }
}
