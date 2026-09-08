package com.minicrm.application.service;

import com.minicrm.application.port.CustomerQuery;
import com.minicrm.application.service.support.FakeCustomerRepository;
import com.minicrm.domain.shared.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CustomerService service = new CustomerService(new FakeCustomerRepository(), clock);

    @Test
    void createsAndRetrievesACustomer() {
        var created = service.createCustomer("Acme", "Retail", "ops@acme.test");

        var fetched = service.getCustomer(created.id());

        assertThat(fetched.name()).isEqualTo("Acme");
        assertThat(fetched.createdAt()).isEqualTo(clock.instant());
    }

    @Test
    void missingCustomerRaisesNotFound() {
        assertThatThrownBy(() -> service.getCustomer(CustomerId.newId()))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void searchFindsByNameSubstring() {
        service.createCustomer("Acme Corp", "Retail", "a@acme.test");
        service.createCustomer("Globex", "Mfg", "a@globex.test");

        var results = service.searchCustomers(CustomerQuery.of("acme"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Acme Corp");
    }
}
