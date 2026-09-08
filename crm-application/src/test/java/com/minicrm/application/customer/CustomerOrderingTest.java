package com.minicrm.application.customer;

import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerOrderingTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void ordersCaseInsensitivelyByName() {
        var globex = Customer.create(CustomerId.newId(), "globex", "Mfg", new EmailAddress("a@globex.test"), NOW);
        var acme = Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("a@acme.test"), NOW);

        List<Customer> customers = new ArrayList<>(List.of(globex, acme));
        customers.sort(CustomerOrdering.BY_NAME);

        assertThat(customers).containsExactly(acme, globex);
    }
}
