package com.minicrm.application.util;

import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerImportTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void copiesEveryElementFromSourceIntoDestination() {
        var source = List.of(
                Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("a@acme.test"), NOW),
                Customer.create(CustomerId.newId(), "Globex", "Mfg", new EmailAddress("a@globex.test"), NOW));

        // destination typed as Collection<Object> demonstrates "? super Customer": a
        // Customer-only destination isn't required, just one that can accept a Customer.
        List<Object> destination = new ArrayList<>();

        CustomerImport.importInto(source, destination);

        assertThat(destination).containsExactlyElementsOf(source);
    }
}
