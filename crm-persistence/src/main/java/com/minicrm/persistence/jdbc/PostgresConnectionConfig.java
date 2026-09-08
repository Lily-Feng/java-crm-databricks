package com.minicrm.persistence.jdbc;

import java.util.Objects;

/**
 * Shared by local Docker Postgres and Lakebase (§4): only the values differ, never the
 * repository code that consumes the resulting {@link javax.sql.DataSource}. §6: "Start
 * with a local pool of 10 and configurable bulkhead limits."
 */
public record PostgresConnectionConfig(
        String jdbcUrl,
        String username,
        String password,
        String poolName,
        int maxPoolSize,
        long connectionTimeoutMillis) {

    public static final int DEFAULT_MAX_POOL_SIZE = 10;
    public static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 5_000;

    public PostgresConnectionConfig {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(poolName, "poolName must not be null");
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("maxPoolSize must be at least 1: " + maxPoolSize);
        }
        if (connectionTimeoutMillis < 1) {
            throw new IllegalArgumentException(
                    "connectionTimeoutMillis must be at least 1: " + connectionTimeoutMillis);
        }
    }

    /** Local Docker defaults; environment variables allow other deployment settings. */
    public static PostgresConnectionConfig local() {
        return local(
                System.getenv().getOrDefault("CRM_DB_URL", "jdbc:postgresql://localhost:5432/minicrm"),
                System.getenv().getOrDefault("CRM_DB_USERNAME", "postgres"),
                System.getenv().getOrDefault("CRM_DB_PASSWORD", "test"));
    }

    public static PostgresConnectionConfig local(String jdbcUrl, String username, String password) {
        return new PostgresConnectionConfig(
                jdbcUrl, username, password, "local-postgres",
                DEFAULT_MAX_POOL_SIZE, DEFAULT_CONNECTION_TIMEOUT_MILLIS);
    }
}
