package com.minicrm.persistence.jdbc;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;

/**
 * §8/§9.2: "versioned migrations" and "a migration ledger; re-running successful setup
 * is harmless." Flyway's own schema history table gives both for free, applied to the
 * one shared DDL (infra/databricks/sql/postgres, §5.1) regardless of whether
 * {@code dataSource} points at local Docker Postgres or Lakebase.
 */
public final class SchemaMigrator {

    private SchemaMigrator() {
    }

    public static MigrateResult migrate(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate();
    }
}
