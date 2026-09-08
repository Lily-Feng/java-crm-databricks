package com.minicrm.application.service;

import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.customer.Contact;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.opportunity.Opportunity;

import java.util.List;
import java.util.Objects;

/**
 * Backs {@code GET /customers/{id}/360} (§3): customer, contacts/tags, opportunities and
 * activities read independently and composed here — not a guaranteed transactional
 * snapshot across the three branches (§3).
 */
public record Customer360(Customer customer, List<Contact> contacts, List<Opportunity> opportunities,
                           List<Activity> activities) {

    public Customer360 {
        Objects.requireNonNull(customer, "customer must not be null");
        contacts = List.copyOf(contacts);
        opportunities = List.copyOf(opportunities);
        activities = List.copyOf(activities);
    }
}
