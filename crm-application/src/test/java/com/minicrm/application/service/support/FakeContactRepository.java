package com.minicrm.application.service.support;

import com.minicrm.application.port.ContactRepository;
import com.minicrm.domain.customer.Contact;
import com.minicrm.domain.shared.CustomerId;

import java.util.ArrayList;
import java.util.List;

public final class FakeContactRepository implements ContactRepository {

    private final List<Contact> contacts = new ArrayList<>();

    public void addFixture(Contact contact) {
        contacts.add(contact);
    }

    @Override
    public List<Contact> findByCustomerId(CustomerId customerId) {
        return contacts.stream().filter(c -> c.customerId().equals(customerId)).toList();
    }
}
