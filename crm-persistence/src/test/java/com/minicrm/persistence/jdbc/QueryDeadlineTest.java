package com.minicrm.persistence.jdbc;

import com.minicrm.persistence.jdbc.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * §5.3: "query deadlines." A one-second {@code setQueryTimeout} against a query that
 * deliberately takes five seconds ({@code pg_sleep(5)}) proves the deadline is real,
 * rather than trusting that every repository method remembered to call it.
 */
class QueryDeadlineTest {

    @Test
    void aQueryExceedingItsTimeoutIsCancelled() throws SQLException {
        try (var connection = PostgresTestSupport.dataSource().getConnection();
             var statement = connection.prepareStatement("SELECT pg_sleep(5)")) {
            statement.setQueryTimeout(1);

            var exception = catchThrowableOfType(SQLException.class, statement::execute);
            assertThat((Throwable) exception).isNotNull();
            assertThat(exception.getSQLState()).isEqualTo("57014"); // query_canceled
        }
    }
}
