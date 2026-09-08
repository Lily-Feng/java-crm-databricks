package com.minicrm.persistence.jdbc.support;

import com.minicrm.persistence.jdbc.PostgresConnectionConfig;
import com.minicrm.persistence.jdbc.SchemaMigrator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Connects to the existing local Postgres database. Tests own only the minicrm_test
 * schema; the container lifecycle and application tables are managed separately.
 */
public final class PostgresTestSupport {
    private static final DataSource DATA_SOURCE;

    static {
        var config = PostgresConnectionConfig.local();
        try (var connection = DriverManager.getConnection(
                config.jdbcUrl(), config.username(), config.password());
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS minicrm_test");
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Cannot prepare test schema. Start local Postgres (docker start minicrm-postgres).", e);
        }
        // Set the schema on every pooled connection before migrations or test queries.
        var hikariConfig = new com.zaxxer.hikari.HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setSchema("minicrm_test");
        hikariConfig.setPoolName("test-postgres");
        hikariConfig.setMaximumPoolSize(config.maxPoolSize());
        hikariConfig.setConnectionTimeout(config.connectionTimeoutMillis());
        var dataSource = new com.zaxxer.hikari.HikariDataSource(hikariConfig);
        Runtime.getRuntime().addShutdownHook(new Thread(dataSource::close));
        DATA_SOURCE = dataSource;
        SchemaMigrator.migrate(DATA_SOURCE);
    }

    private PostgresTestSupport() {
    }

    public static DataSource dataSource() {
        return DATA_SOURCE;
    }

    /** Test isolation without restarting the container or re-running migrations. */
    public static void truncateAll() {
        try (Connection connection = DATA_SOURCE.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE tags, activities, opportunities, contacts, customers CASCADE");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to truncate test schema", e);
        }
    }

}
