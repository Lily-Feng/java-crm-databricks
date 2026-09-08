package com.minicrm.application.service;

import com.minicrm.application.port.CustomerQuery;
import com.minicrm.application.port.CustomerRepository;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Backs {@code POST /customers}, {@code GET /customers/{id}} and
 * {@code GET /customers?search=...} (§3). Thin on purpose: invariant enforcement lives in
 * {@link Customer} itself, not here.
 */
public final class CustomerService {

    private final CustomerRepository customers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Customer createCustomer(String name, String industry, String email) {
        var customer = Customer.create(CustomerId.newId(), name, industry, new EmailAddress(email), clock.instant());
        return customers.save(customer);
    }

    public Customer getCustomer(CustomerId id) {
        return customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    public List<Customer> searchCustomers(CustomerQuery query) {
        return customers.search(query);
    }
}
