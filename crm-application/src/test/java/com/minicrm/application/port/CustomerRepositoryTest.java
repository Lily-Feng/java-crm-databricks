package com.minicrm.application.port;

import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    /** Minimal spy: just enough of a CustomerRepository to observe saveAll's dispatch to save. */
    private static final class SpyRepository implements CustomerRepository {
        private final Map<CustomerId, Customer> saved = new LinkedHashMap<>();

        @Override
        public Optional<Customer> findById(CustomerId id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public List<Customer> search(CustomerQuery query) {
            return List.copyOf(saved.values());
        }

        @Override
        public Customer save(Customer customer) {
            saved.put(customer.id(), customer);
            return customer;
        }
    }

    @Test
    void saveAllAcceptsAProducerExtendsCollectionAndSavesEachElement() {
        var repository = new SpyRepository();
        // A List<Customer> is-a Collection<? extends Customer>: the PECS bound accepts
        // the exact type as well as any subtype the caller might have.
        List<Customer> batch = new ArrayList<>(List.of(
                Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("ops@acme.test"), NOW),
                Customer.create(CustomerId.newId(), "Globex", "Manufacturing", new EmailAddress("ops@globex.test"), NOW)));

        repository.saveAll(batch);

        assertThat(repository.saved).hasSize(2);
    }
}
