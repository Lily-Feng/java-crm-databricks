package com.minicrm.persistence.jdbc;

import com.minicrm.application.port.ActivityRepository;
import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.activity.Call;
import com.minicrm.domain.activity.Email;
import com.minicrm.domain.activity.Meeting;
import com.minicrm.domain.shared.ActivityId;
import com.minicrm.domain.shared.CustomerId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * §5.2: idempotency enforced by the database, not application logic —
 * {@code INSERT ... ON CONFLICT (customer_id, idempotency_key) DO NOTHING} either wins
 * (1 row affected: this call's activity is now the stored one) or loses (0 rows: some
 * other transaction's insert already committed under that key, possibly this same
 * request replayed). Either way, a bounded follow-up read returns whichever row is
 * actually stored — no application-level retry loop or manual rollback needed, because
 * the database's own unique index is what serializes concurrent attempts.
 */
public final class JdbcActivityRepository implements ActivityRepository {

    private final DataSource dataSource;
    private final int queryTimeoutSeconds;

    public JdbcActivityRepository(DataSource dataSource, int queryTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public List<Activity> findByCustomerId(CustomerId customerId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT * FROM activities WHERE customer_id = ? ORDER BY occurred_at")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setObject(1, customerId.value());
            List<Activity> activities = new ArrayList<>();
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    activities.add(mapActivity(resultSet));
                }
            }
            return activities;
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public Optional<StoredActivity> findByIdempotencyKey(CustomerId customerId, String idempotencyKey) {
        try (var connection = dataSource.getConnection()) {
            return selectByIdempotencyKey(connection, customerId, idempotencyKey);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public StoredActivity save(Activity activity, String idempotencyKey, String requestHash) {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO activities
                        (activity_id, customer_id, type, subject, occurred_at, created_by,
                         duration_minutes, body_preview, attendees, idempotency_key, request_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (customer_id, idempotency_key) DO NOTHING
                    """)) {
                statement.setQueryTimeout(queryTimeoutSeconds);
                bind(connection, statement, activity, idempotencyKey, requestHash);
                int inserted = statement.executeUpdate();
                if (inserted == 1) {
                    return new StoredActivity(activity, requestHash);
                }
            }
            return selectByIdempotencyKey(connection, activity.customerId(), idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Insert conflicted under key " + idempotencyKey + " but no row is stored"));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Optional<StoredActivity> selectByIdempotencyKey(
            Connection connection, CustomerId customerId, String idempotencyKey) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM activities WHERE customer_id = ? AND idempotency_key = ?")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setObject(1, customerId.value());
            statement.setString(2, idempotencyKey);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredActivity(mapActivity(resultSet), resultSet.getString("request_hash")));
            }
        }
    }

    private void bind(
            Connection connection, PreparedStatement statement, Activity activity, String idempotencyKey,
            String requestHash) throws SQLException {
        statement.setObject(1, activity.id().value());
        statement.setObject(2, activity.customerId().value());
        statement.setString(3, typeOf(activity));
        statement.setString(4, subjectOf(activity));
        statement.setTimestamp(5, Timestamp.from(activity.occurredAt()));
        statement.setString(6, activity.createdBy());

        switch (activity) {
            case Call c -> {
                statement.setInt(7, c.durationMinutes());
                statement.setNull(8, Types.VARCHAR);
                statement.setNull(9, Types.ARRAY);
            }
            case Email e -> {
                statement.setNull(7, Types.INTEGER);
                statement.setString(8, e.bodyPreview());
                statement.setNull(9, Types.ARRAY);
            }
            case Meeting m -> {
                statement.setNull(7, Types.INTEGER);
                statement.setNull(8, Types.VARCHAR);
                statement.setArray(9, connection.createArrayOf("text", m.attendees().toArray()));
            }
        }

        statement.setString(10, idempotencyKey);
        statement.setString(11, requestHash);
    }

    private static String typeOf(Activity activity) {
        return switch (activity) {
            case Call ignored -> "CALL";
            case Email ignored -> "EMAIL";
            case Meeting ignored -> "MEETING";
        };
    }

    private static String subjectOf(Activity activity) {
        return switch (activity) {
            case Call c -> c.subject();
            case Email e -> e.subject();
            case Meeting m -> m.subject();
        };
    }

    private static Activity mapActivity(ResultSet resultSet) throws SQLException {
        var id = new ActivityId((UUID) resultSet.getObject("activity_id"));
        var customerId = new CustomerId((UUID) resultSet.getObject("customer_id"));
        var subject = resultSet.getString("subject");
        var occurredAt = resultSet.getTimestamp("occurred_at").toInstant();
        var createdBy = resultSet.getString("created_by");

        return switch (resultSet.getString("type")) {
            case "CALL" -> new Call(id, customerId, subject, occurredAt, createdBy, resultSet.getInt("duration_minutes"));
            case "EMAIL" -> new Email(id, customerId, subject, occurredAt, createdBy, resultSet.getString("body_preview"));
            case "MEETING" -> {
                var sqlArray = resultSet.getArray("attendees");
                var attendees = sqlArray == null ? List.<String>of() : List.of((String[]) sqlArray.getArray());
                yield new Meeting(id, customerId, subject, occurredAt, createdBy, attendees);
            }
            case String other -> throw new IllegalStateException("Unknown activity type in database: " + other);
        };
    }
}
