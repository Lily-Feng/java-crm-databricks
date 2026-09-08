package com.minicrm.persistence.inmemory;

import com.minicrm.application.port.CustomerQuery;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCustomerRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final InMemoryCustomerRepository repository = new InMemoryCustomerRepository();

    @Test
    void savedCustomerIsFoundById() {
        var customer = Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("a@acme.test"), NOW);
        repository.save(customer);

        assertThat(repository.findById(customer.id())).contains(customer);
    }

    @Test
    void searchIsCaseInsensitiveAndOrderedByName() {
        repository.save(Customer.create(CustomerId.newId(), "globex", "Mfg", new EmailAddress("a@globex.test"), NOW));
        repository.save(Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("a@acme.test"), NOW));

        var results = repository.search(CustomerQuery.of("e")); // both "Acme" and "globex" contain "e"

        assertThat(results).extracting(Customer::name).containsExactly("Acme", "globex");
    }

    @Test
    void searchIsBoundedByLimit() {
        for (int i = 0; i < 5; i++) {
            repository.save(Customer.create(CustomerId.newId(), "Customer " + i, "Retail",
                    new EmailAddress("c" + i + "@test.test"), NOW));
        }

        var results = repository.search(new CustomerQuery(java.util.Optional.empty(), 2));

        assertThat(results).hasSize(2);
    }
}
