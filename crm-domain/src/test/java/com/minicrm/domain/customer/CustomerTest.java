package com.minicrm.domain.customer;

import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createStartsAtVersionZeroWithNoTags() {
        var customer = Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("ops@acme.test"), NOW);

        assertThat(customer.version()).isZero();
        assertThat(customer.tags()).isEmpty();
        assertThat(customer.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() ->
                        Customer.create(CustomerId.newId(), "  ", "Retail", new EmailAddress("ops@acme.test"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tagsCanBeAddedAndRemoved() {
        var customer = Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("ops@acme.test"), NOW);

        customer.addTag("vip");
        customer.addTag("vip");
        assertThat(customer.tags()).containsExactly("vip");

        customer.removeTag("vip");
        assertThat(customer.tags()).isEmpty();
    }

    @Test
    void tagsAreNotExposedAsAMutableView() {
        var customer = Customer.create(CustomerId.newId(), "Acme", "Retail", new EmailAddress("ops@acme.test"), NOW);

        assertThatThrownBy(() -> customer.tags().add("vip"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
