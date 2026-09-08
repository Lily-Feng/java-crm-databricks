package com.minicrm.persistence.jdbc;

import com.minicrm.application.customer.CustomerOrdering;
import com.minicrm.application.port.CustomerQuery;
import com.minicrm.application.port.CustomerRepository;
import com.minicrm.domain.customer.Customer;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * §4/§5.3: the one JDBC/Postgres implementation shared, unchanged, by local Docker
 * Postgres and Lakebase — only the {@link DataSource} it is constructed with differs.
 */
public final class JdbcCustomerRepository implements CustomerRepository {

    private final DataSource dataSource;
    private final int queryTimeoutSeconds;

    public JdbcCustomerRepository(DataSource dataSource, int queryTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        try (var connection = dataSource.getConnection()) {
            var customer = selectOne(connection, id);
            if (customer.isEmpty()) {
                return Optional.empty();
            }
            var tags = selectTags(connection, List.of(id));
            return customer.map(c -> withTags(c, tags.getOrDefault(id, Set.of())));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public List<Customer> search(CustomerQuery query) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT customer_id, name, industry, email, created_at, updated_at, version
                     FROM customers
                     WHERE (? = '' OR name ILIKE '%' || ? || '%')
                     ORDER BY name
                     LIMIT ?
                     """)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            var search = query.search().orElse("");
            statement.setString(1, search);
            statement.setString(2, search);
            statement.setInt(3, query.limit());

            List<Customer> withoutTags = new ArrayList<>();
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    withoutTags.add(mapRow(resultSet));
                }
            }
            var ids = withoutTags.stream().map(Customer::id).toList();
            var tagsByCustomer = selectTags(connection, ids);
            return withoutTags.stream()
                    .map(c -> withTags(c, tagsByCustomer.getOrDefault(c.id(), Set.of())))
                    .sorted(CustomerOrdering.BY_NAME)
                    .toList();
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public Customer save(Customer customer) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertCustomer(connection, customer);
                replaceTags(connection, customer);
                connection.commit();
                return customer;
            } catch (SQLException e) {
                connection.rollback();
                throw new UncheckedSqlException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Optional<Customer> selectOne(Connection connection, CustomerId id) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT customer_id, name, industry, email, created_at, updated_at, version
                FROM customers WHERE customer_id = ?
                """)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setObject(1, id.value());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    private Map<CustomerId, Set<String>> selectTags(Connection connection, List<CustomerId> ids) throws SQLException {
        Map<CustomerId, Set<String>> tags = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return tags;
        }
        Array idArray = connection.createArrayOf("uuid", ids.stream().map(CustomerId::value).toArray());
        try (var statement = connection.prepareStatement(
                "SELECT customer_id, tag FROM tags WHERE customer_id = ANY(?)")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setArray(1, idArray);
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    var customerId = new CustomerId((UUID) resultSet.getObject("customer_id"));
                    tags.computeIfAbsent(customerId, k -> new LinkedHashSet<>()).add(resultSet.getString("tag"));
                }
            }
        }
        return tags;
    }

    private void upsertCustomer(Connection connection, Customer customer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO customers (customer_id, name, industry, email, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (customer_id) DO UPDATE SET
                    name = EXCLUDED.name, industry = EXCLUDED.industry, email = EXCLUDED.email,
                    updated_at = EXCLUDED.updated_at, version = EXCLUDED.version
                """)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setObject(1, customer.id().value());
            statement.setString(2, customer.name());
            statement.setString(3, customer.industry());
            statement.setString(4, customer.email().value());
            statement.setTimestamp(5, Timestamp.from(customer.createdAt()));
            statement.setTimestamp(6, Timestamp.from(customer.updatedAt()));
            statement.setInt(7, customer.version());
            statement.executeUpdate();
        }
    }

    private void replaceTags(Connection connection, Customer customer) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM tags WHERE customer_id = ?")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setObject(1, customer.id().value());
            statement.executeUpdate();
        }
        if (customer.tags().isEmpty()) {
            return;
        }
        try (var statement = connection.prepareStatement("INSERT INTO tags (customer_id, tag) VALUES (?, ?)")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            for (String tag : customer.tags()) {
                statement.setObject(1, customer.id().value());
                statement.setString(2, tag);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Customer mapRow(ResultSet resultSet) throws SQLException {
        var id = new CustomerId((UUID) resultSet.getObject("customer_id"));
        return new Customer(
                id,
                resultSet.getString("name"),
                resultSet.getString("industry"),
                new EmailAddress(resultSet.getString("email")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")),
                resultSet.getInt("version"),
                Set.of());
    }

    private static Customer withTags(Customer customer, Set<String> tags) {
        return new Customer(
                customer.id(), customer.name(), customer.industry(), customer.email(),
                customer.createdAt(), customer.updatedAt(), customer.version(), tags);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }
}
