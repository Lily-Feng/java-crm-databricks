package com.minicrm.persistence.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * §5.3: "Use a bounded HikariCP pool, parameterized statements, query deadlines, and
 * try-with-resources." Lakebase keeps its own credential-rotating factory in
 * crm-databricks (different auth flow, §5.3/§9.2), but both ultimately hand the same
 * {@link javax.sql.DataSource} contract to crm-persistence's repository classes.
 */
public final class PostgresDataSourceFactory {

    private PostgresDataSourceFactory() {
    }

    public static HikariDataSource create(PostgresConnectionConfig config) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setPoolName(config.poolName());
        hikariConfig.setMaximumPoolSize(config.maxPoolSize());
        hikariConfig.setConnectionTimeout(config.connectionTimeoutMillis());
        return new HikariDataSource(hikariConfig);
    }
}
