package com.minicrm.persistence.jdbc;

import com.minicrm.application.port.CustomerQuery;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import com.minicrm.persistence.jdbc.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCustomerRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final JdbcCustomerRepository repository = new JdbcCustomerRepository(PostgresTestSupport.dataSource(), 5);

    @BeforeEach
    void resetSchema() {
        PostgresTestSupport.truncateAll();
    }

    @Test
    void savedCustomerRoundTripsThroughRealPostgres() {
        var customer = Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("a@acme.test"), NOW);
        customer.addTag("vip");
        customer.addTag("renewal-risk");
        repository.save(customer);

        Optional<Customer> found = repository.findById(customer.id());

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Acme");
        assertThat(found.get().tags()).isEqualTo(Set.of("vip", "renewal-risk"));
    }

    @Test
    void savingAgainReplacesTagsRatherThanAccumulatingThem() {
        var customer = Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("a@acme.test"), NOW);
        customer.addTag("vip");
        repository.save(customer);

        customer.removeTag("vip");
        customer.addTag("churned");
        repository.save(customer);

        assertThat(repository.findById(customer.id()).orElseThrow().tags()).containsExactly("churned");
    }

    @Test
    void searchIsBoundedCaseInsensitiveAndOrderedByName() {
        repository.save(Customer.create(CustomerId.newId(), "globex", "Mfg", new EmailAddress("a@globex.test"), NOW));
        repository.save(Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("a@acme.test"), NOW));
        repository.save(Customer.create(CustomerId.newId(), "Acorn", "Other", new EmailAddress("a@acorn.test"), NOW));

        var results = repository.search(CustomerQuery.of("e")); // "Acme" and "globex" both contain "e"

        assertThat(results).extracting(Customer::name).containsExactly("Acme", "globex");
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownCustomer() {
        assertThat(repository.findById(CustomerId.newId())).isEmpty();
    }
}
