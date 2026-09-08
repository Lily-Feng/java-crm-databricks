package com.minicrm.application.service;

import com.minicrm.application.port.ActivityRepository;
import com.minicrm.application.port.ContactRepository;
import com.minicrm.application.port.CustomerRepository;
import com.minicrm.application.port.OpportunityRepository;
import com.minicrm.domain.shared.CustomerId;

import java.util.Objects;

/**
 * Version A (design-spec §6): sequential baseline. Versions B–E (Executor/Future,
 * CompletableFuture, virtual threads, structured concurrency) arrive at Milestones 6–8
 * and 13 as alternative implementations selectable for comparison, not replacements of
 * this one.
 */
public final class Customer360Service {

    private final CustomerRepository customers;
    private final ContactRepository contacts;
    private final OpportunityRepository opportunities;
    private final ActivityRepository activities;

    public Customer360Service(
            CustomerRepository customers,
            ContactRepository contacts,
            OpportunityRepository opportunities,
            ActivityRepository activities) {
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
        this.contacts = Objects.requireNonNull(contacts, "contacts must not be null");
        this.opportunities = Objects.requireNonNull(opportunities, "opportunities must not be null");
        this.activities = Objects.requireNonNull(activities, "activities must not be null");
    }

    public Customer360 getCustomer360(CustomerId id) {
        var customer = customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
        var customerContacts = contacts.findByCustomerId(id);
        var customerOpportunities = opportunities.findByCustomerId(id);
        var customerActivities = activities.findByCustomerId(id);
        return new Customer360(customer, customerContacts, customerOpportunities, customerActivities);
    }
}
