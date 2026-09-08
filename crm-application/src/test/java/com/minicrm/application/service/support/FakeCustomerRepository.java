package com.minicrm.application.service.support;

import com.minicrm.application.port.CustomerQuery;
import com.minicrm.application.port.CustomerRepository;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A minimal test double, not a production adapter: crm-application's tests cannot depend
 * on crm-persistence (crm-persistence depends on crm-application), so service tests use
 * this instead. The real in-memory adapter is crm-persistence's InMemoryCustomerRepository.
 */
public final class FakeCustomerRepository implements CustomerRepository {

    private final Map<CustomerId, Customer> customers = new LinkedHashMap<>();

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public List<Customer> search(CustomerQuery query) {
        return customers.values().stream()
                .filter(c -> query.search().isEmpty()
                        || c.name().toLowerCase().contains(query.search().get().toLowerCase()))
                .limit(query.limit())
                .toList();
    }

    @Override
    public Customer save(Customer customer) {
        customers.put(customer.id(), customer);
        return customer;
    }
}
