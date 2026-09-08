package com.minicrm.domain.customer;

import com.minicrm.domain.shared.ContactId;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;

import java.util.Objects;

public record Contact(ContactId id, CustomerId customerId, String name, EmailAddress email, String role) {

    public Contact {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
        role = role == null ? "" : role;
    }
}
