package com.minicrm.persistence.jdbc;

import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;
import com.minicrm.persistence.jdbc.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcContactRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final JdbcCustomerRepository customers = new JdbcCustomerRepository(PostgresTestSupport.dataSource(), 5);
    private final JdbcContactRepository contacts = new JdbcContactRepository(PostgresTestSupport.dataSource(), 5);

    @BeforeEach
    void resetSchema() {
        PostgresTestSupport.truncateAll();
    }

    @Test
    void returnsOnlyContactsForTheRequestedCustomerOrderedByName() throws SQLException {
        var customerId = CustomerId.newId();
        customers.save(Customer.create(customerId, "Acme", "Retail", new EmailAddress("a@acme.test"), NOW));
        var otherCustomerId = CustomerId.newId();
        customers.save(Customer.create(otherCustomerId, "Globex", "Mfg", new EmailAddress("a@globex.test"), NOW));

        insertContact(customerId, "Zed", "z@acme.test", "Sales");
        insertContact(customerId, "Amy", "a2@acme.test", "CFO");
        insertContact(otherCustomerId, "Bob", "b@globex.test", "CTO");

        var found = contacts.findByCustomerId(customerId);

        assertThat(found).extracting(c -> c.name()).containsExactly("Amy", "Zed");
    }

    private void insertContact(CustomerId customerId, String name, String email, String role) throws SQLException {
        try (var connection = PostgresTestSupport.dataSource().getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO contacts (contact_id, customer_id, name, email, role) VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, customerId.value());
            statement.setString(3, name);
            statement.setString(4, email);
            statement.setString(5, role);
            statement.executeUpdate();
        }
    }
}
