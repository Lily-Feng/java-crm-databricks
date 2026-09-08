package com.minicrm.persistence.jdbc;

import com.minicrm.application.port.OpportunityRepository;
import com.minicrm.application.service.OpportunityNotFoundException;
import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.opportunity.StaleVersionException;
import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.Money;
import com.minicrm.domain.shared.OpportunityId;
import com.minicrm.domain.shared.Probability;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * §5.2's optimistic-locking SQL, for real:
 * {@code UPDATE opportunities SET ... version = version + 1 WHERE opportunity_id = ? AND version = ?}.
 * "Check affected rows and distinguish missing entity from stale version" — done here by
 * a follow-up read only on the (rare) zero-rows-affected path, not on every write.
 */
public final class JdbcOpportunityRepository implements OpportunityRepository {

    private final DataSource dataSource;
    private final int queryTimeoutSeconds;

    public JdbcOpportunityRepository(DataSource dataSource, int queryTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public Optional<Opportunity> findById(OpportunityId id) {
        try (var connection = dataSource.getConnection()) {
            return selectById(connection, id);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public List<Opportunity> findByCustomerId(CustomerId customerId) {
        return selectMany(
                "SELECT * FROM opportunities WHERE customer_id = ? ORDER BY created_at",
                statement -> statement.setObject(1, customerId.value()));
    }

    @Override
    public List<Opportunity> findAll() {
        return selectMany("SELECT * FROM opportunities ORDER BY created_at", statement -> { });
    }

    @Override
    public Opportunity save(Opportunity opportunity) {
        int expectedPriorVersion = opportunity.version() - 1;
        try (var connection = dataSource.getConnection()) {
            if (expectedPriorVersion < 0) {
                insert(connection, opportunity);
                return opportunity;
            }
            int updated = update(connection, opportunity, expectedPriorVersion);
            if (updated == 1) {
                return opportunity;
            }
            // §5.2: distinguish "no such opportunity" from "someone else already moved it".
            var current = selectById(connection, opportunity.id());
            if (current.isEmpty()) {
                throw new OpportunityNotFoundException(opportunity.id());
            }
            throw new StaleVersionException(opportunity.id(), expectedPriorVersion, current.get().version());
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private void insert(Connection connection, Opportunity opportunity) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO opportunities
                    (opportunity_id, customer_id, name, amount_minor, currency, stage, probability,
                     owner, notes, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            bindAll(statement, opportunity);
            statement.executeUpdate();
        }
    }

    private int update(Connection connection, Opportunity opportunity, int expectedPriorVersion) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE opportunities
                SET name = ?, amount_minor = ?, currency = ?, stage = ?, probability = ?,
                    owner = ?, notes = ?, version = ?, updated_at = ?
                WHERE opportunity_id = ? AND version = ?
                """)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setString(1, opportunity.name());
            statement.setLong(2, opportunity.amount().minorUnits());
            statement.setString(3, opportunity.amount().currency().getCurrencyCode());
            statement.setString(4, opportunity.stage().name());
            statement.setDouble(5, opportunity.probability().value());
            statement.setString(6, opportunity.owner());
            statement.setString(7, opportunity.notes());
            statement.setInt(8, opportunity.version());
            statement.setTimestamp(9, Timestamp.from(opportunity.updatedAt()));
            statement.setObject(10, opportunity.id().value());
            statement.setInt(11, expectedPriorVersion);
            return statement.executeUpdate();
        }
    }

    private void bindAll(PreparedStatement statement, Opportunity opportunity) throws SQLException {
        statement.setObject(1, opportunity.id().value());
        statement.setObject(2, opportunity.customerId().value());
        statement.setString(3, opportunity.name());
        statement.setLong(4, opportunity.amount().minorUnits());
        statement.setString(5, opportunity.amount().currency().getCurrencyCode());
        statement.setString(6, opportunity.stage().name());
        statement.setDouble(7, opportunity.probability().value());
        statement.setString(8, opportunity.owner());
        statement.setString(9, opportunity.notes());
        statement.setInt(10, opportunity.version());
        statement.setTimestamp(11, Timestamp.from(opportunity.createdAt()));
        statement.setTimestamp(12, Timestamp.from(opportunity.updatedAt()));
    }

    private Optional<Opportunity> selectById(Connection connection, OpportunityId id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM opportunities WHERE opportunity_id = ?")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setObject(1, id.value());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    private List<Opportunity> selectMany(String sql, SqlBinder binder) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            binder.bind(statement);
            List<Opportunity> results = new ArrayList<>();
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private static Opportunity mapRow(ResultSet resultSet) throws SQLException {
        return new Opportunity(
                new OpportunityId((UUID) resultSet.getObject("opportunity_id")),
                new CustomerId((UUID) resultSet.getObject("customer_id")),
                resultSet.getString("name"),
                new Money(Currency.getInstance(resultSet.getString("currency")), resultSet.getLong("amount_minor")),
                Stage.valueOf(resultSet.getString("stage")),
                Probability.of(resultSet.getDouble("probability")),
                resultSet.getString("owner"),
                resultSet.getString("notes"),
                resultSet.getInt("version"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
