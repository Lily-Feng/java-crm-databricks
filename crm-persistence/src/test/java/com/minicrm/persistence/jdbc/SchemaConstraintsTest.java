package com.minicrm.persistence.jdbc;

import com.minicrm.persistence.jdbc.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §5.2: "Postgres enforces PK/FK/UNIQUE and supplies real transactions." Exercised
 * directly against raw SQL here (not through a repository, which upserts around PK
 * conflicts) so the schema's own guarantees are what's under test, not application code.
 */
class SchemaConstraintsTest {

    @BeforeEach
    void resetSchema() {
        PostgresTestSupport.truncateAll();
    }

    @Test
    void primaryKeyRejectsADuplicateCustomerId() throws SQLException {
        var id = UUID.randomUUID();
        insertCustomer(id, "a@test.test");

        assertThatThrownBy(() -> insertCustomer(id, "b@test.test")).isInstanceOf(SQLException.class);
    }

    @Test
    void checkConstraintRejectsANegativeOpportunityAmount() throws SQLException {
        var customerId = UUID.randomUUID();
        insertCustomer(customerId, "a@test.test");

        assertThatThrownBy(() -> {
            try (var connection = PostgresTestSupport.dataSource().getConnection();
                 var statement = connection.prepareStatement("""
                         INSERT INTO opportunities
                             (opportunity_id, customer_id, name, amount_minor, currency, stage,
                              probability, owner, notes, version, created_at, updated_at)
                         VALUES (?, ?, 'Deal', -100, 'USD', 'QUALIFIED', 0.5, 'alice', '', 0, now(), now())
                         """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, customerId);
                statement.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void uniqueConstraintRejectsADuplicateIdempotencyKeyForTheSameCustomer() throws SQLException {
        var customerId = UUID.randomUUID();
        insertCustomer(customerId, "a@test.test");
        insertCall(customerId, "key-1");

        assertThatThrownBy(() -> insertCall(customerId, "key-1")).isInstanceOf(SQLException.class);
    }

    private static void insertCustomer(UUID id, String email) throws SQLException {
        try (var connection = PostgresTestSupport.dataSource().getConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO customers (customer_id, name, industry, email, created_at, updated_at, version)
                     VALUES (?, 'Acme', 'Retail', ?, ?, ?, 0)
                     """)) {
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.setObject(3, java.sql.Timestamp.from(Instant.now()));
            statement.setObject(4, java.sql.Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private static void insertCall(UUID customerId, String idempotencyKey) throws SQLException {
        try (var connection = PostgresTestSupport.dataSource().getConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO activities
                         (activity_id, customer_id, type, subject, occurred_at, created_by,
                          duration_minutes, idempotency_key, request_hash)
                     VALUES (?, ?, 'CALL', 'Check-in', now(), 'alice', 10, ?, 'hash')
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, customerId);
            statement.setString(3, idempotencyKey);
            statement.executeUpdate();
        }
    }
}
