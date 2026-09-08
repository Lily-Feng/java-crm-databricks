package com.minicrm.application.service;

import com.minicrm.application.service.support.FakeActivityRepository;
import com.minicrm.application.service.support.FakeContactRepository;
import com.minicrm.application.service.support.FakeCustomerRepository;
import com.minicrm.application.service.support.FakeOpportunityRepository;
import com.minicrm.domain.customer.Contact;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.shared.ContactId;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.Probability;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Customer360ServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final FakeCustomerRepository customers = new FakeCustomerRepository();
    private final FakeContactRepository contacts = new FakeContactRepository();
    private final FakeOpportunityRepository opportunities = new FakeOpportunityRepository();
    private final FakeActivityRepository activities = new FakeActivityRepository();
    private final Customer360Service service =
            new Customer360Service(customers, contacts, opportunities, activities);

    @Test
    void aggregatesCustomerContactsOpportunitiesAndActivities() {
        var customerId = CustomerId.newId();
        var customer = Customer.create(customerId, "Acme", "Retail", new EmailAddress("ops@acme.test"), NOW);
        customers.save(customer);
        var contact = new Contact(ContactId.newId(), customerId, "Bob", new EmailAddress("bob@acme.test"), "CFO");
        contacts.addFixture(contact);
        var opportunity = Opportunity.open(
                com.minicrm.domain.shared.OpportunityId.newId(), customerId, "Renewal",
                Money.of("USD", 5_000_00), Probability.of(0.4), "alice", NOW);
        opportunities.save(opportunity);

        var view = service.getCustomer360(customerId);

        assertThat(view.customer()).isEqualTo(customer);
        assertThat(view.contacts()).containsExactly(contact);
        assertThat(view.opportunities()).containsExactly(opportunity);
        assertThat(view.activities()).isEmpty();
    }

    @Test
    void missingCustomerRaisesNotFound() {
        assertThatThrownBy(() -> service.getCustomer360(CustomerId.newId()))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
