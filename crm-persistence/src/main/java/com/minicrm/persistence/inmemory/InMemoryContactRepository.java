package com.minicrm.persistence.inmemory;

import com.minicrm.application.port.ContactRepository;
import com.minicrm.domain.customer.Contact;
import com.minicrm.domain.shared.CustomerId;

import java.util.List;

/**
 * §1.2: "Contacts and tags can initially be fixtures included in Customer 360." There is
 * no write endpoint for contacts, so this adapter is seeded once at construction and is
 * read-only thereafter.
 */
public final class InMemoryContactRepository implements ContactRepository {

    private final List<Contact> fixtures;

    public InMemoryContactRepository(List<Contact> fixtures) {
        this.fixtures = List.copyOf(fixtures);
    }

    @Override
    public List<Contact> findByCustomerId(CustomerId customerId) {
        return fixtures.stream().filter(c -> c.customerId().equals(customerId)).toList();
    }
}
