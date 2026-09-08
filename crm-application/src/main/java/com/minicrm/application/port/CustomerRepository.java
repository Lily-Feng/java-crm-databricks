package com.minicrm.application.port;

import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A port (idea.md §3): application code depends on this interface, never on a concrete
 * adapter. In-memory, Postgres and Lakebase implementations arrive in later milestones
 * (design-spec §4).
 */
public interface CustomerRepository {

    Optional<Customer> findById(CustomerId id);

    List<Customer> search(CustomerQuery query);

    Customer save(Customer customer);

    /**
     * PECS (idea.md §5): {@code source} only ever produces {@code Customer}s, so it is
     * declared {@code ? extends Customer} — the caller may pass a {@code List<Customer>}
     * or a {@code List} of any Customer subtype, and this method can still only read from
     * it, never insert an incompatible element back in.
     */
    default void saveAll(Collection<? extends Customer> customers) {
        for (Customer customer : customers) {
            save(customer);
        }
    }
}
