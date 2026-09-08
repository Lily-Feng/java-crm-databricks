package com.minicrm.application.customer;

import com.minicrm.domain.customer.Customer;

import java.util.Comparator;

/**
 * idea.md §5: Comparable/Comparator review. Bounded search results (§3) need a
 * deterministic order regardless of which repository adapter answers the query.
 */
public final class CustomerOrdering {

    public static final Comparator<Customer> BY_NAME =
            Comparator.comparing(Customer::name, String.CASE_INSENSITIVE_ORDER).thenComparing(c -> c.id().value());

    private CustomerOrdering() {
    }
}
