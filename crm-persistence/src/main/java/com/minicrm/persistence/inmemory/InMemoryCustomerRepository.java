package com.minicrm.persistence.inmemory;

import com.minicrm.application.customer.CustomerOrdering;
import com.minicrm.application.port.CustomerQuery;
import com.minicrm.application.port.CustomerRepository;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * §5.2: "In-memory repositories use atomic map operations/locks for equivalent
 * single-process business invariants." A single {@code save} is one map operation, so a
 * {@link ConcurrentHashMap} is sufficient here — unlike {@link InMemoryOpportunityRepository},
 * there is no read-modify-write business transaction to protect (no versioned update
 * endpoint exists for customers).
 */
public final class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<CustomerId, Customer> customers = new ConcurrentHashMap<>();

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public java.util.List<Customer> search(CustomerQuery query) {
        return customers.values().stream()
                .filter(c -> query.search().isEmpty() || matches(c, query.search().get()))
                .sorted(CustomerOrdering.BY_NAME)
                .limit(query.limit())
                .toList();
    }

    @Override
    public Customer save(Customer customer) {
        customers.put(customer.id(), customer);
        return customer;
    }

    private static boolean matches(Customer customer, String search) {
        return customer.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }
}
