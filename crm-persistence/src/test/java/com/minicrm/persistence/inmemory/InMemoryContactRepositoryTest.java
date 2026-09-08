package com.minicrm.persistence.inmemory;

import com.minicrm.domain.customer.Contact;
import com.minicrm.domain.shared.ContactId;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryContactRepositoryTest {

    @Test
    void returnsOnlyContactsForTheRequestedCustomer() {
        var customerA = CustomerId.newId();
        var customerB = CustomerId.newId();
        var contactA = new Contact(ContactId.newId(), customerA, "Alice", new EmailAddress("a@a.test"), "CFO");
        var contactB = new Contact(ContactId.newId(), customerB, "Bob", new EmailAddress("b@b.test"), "CTO");
        var repository = new InMemoryContactRepository(List.of(contactA, contactB));

        assertThat(repository.findByCustomerId(customerA)).containsExactly(contactA);
    }
}
