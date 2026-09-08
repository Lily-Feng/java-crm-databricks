package com.minicrm.persistence.jdbc;

import com.minicrm.application.port.ContactRepository;
import com.minicrm.domain.customer.Contact;
import com.minicrm.domain.shared.ContactId;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Read-only (§1.2): no write endpoint exists for contacts. */
public final class JdbcContactRepository implements ContactRepository {

    private final DataSource dataSource;
    private final int queryTimeoutSeconds;

    public JdbcContactRepository(DataSource dataSource, int queryTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public List<Contact> findByCustomerId(CustomerId customerId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT contact_id, customer_id, name, email, role
                     FROM contacts WHERE customer_id = ? ORDER BY name
                     """)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setObject(1, customerId.value());
            List<Contact> contacts = new ArrayList<>();
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    contacts.add(new Contact(
                            new ContactId((UUID) resultSet.getObject("contact_id")),
                            new CustomerId((UUID) resultSet.getObject("customer_id")),
                            resultSet.getString("name"),
                            new EmailAddress(resultSet.getString("email")),
                            resultSet.getString("role")));
                }
            }
            return contacts;
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }
}
