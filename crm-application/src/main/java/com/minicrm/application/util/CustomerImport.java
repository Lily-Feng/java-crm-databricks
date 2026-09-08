package com.minicrm.application.util;

import com.minicrm.domain.customer.Customer;

import java.util.Collection;

/**
 * idea.md §5's PECS example verbatim: {@code importCustomers(Iterable<? extends Customer>)}.
 * {@code source} only produces elements ("Producer Extends"); {@code destination} only
 * accepts them ("Consumer Super"). Neither bound could be tightened to an exact
 * {@code Customer} type without rejecting valid callers (e.g. a destination typed as
 * {@code Collection<Object>}).
 */
public final class CustomerImport {

    private CustomerImport() {
    }

    public static void importInto(Iterable<? extends Customer> source, Collection<? super Customer> destination) {
        for (Customer customer : source) {
            destination.add(customer);
        }
    }
}
