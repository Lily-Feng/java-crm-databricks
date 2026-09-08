package com.minicrm.application.port;

import com.minicrm.domain.customer.Contact;
import com.minicrm.domain.shared.CustomerId;

import java.util.List;

/**
 * Read-only port: contacts are fixtures included in Customer 360 for now (design-spec
 * §1.2), so there is no create/update endpoint or method here yet.
 */
public interface ContactRepository {

    List<Contact> findByCustomerId(CustomerId customerId);
}
