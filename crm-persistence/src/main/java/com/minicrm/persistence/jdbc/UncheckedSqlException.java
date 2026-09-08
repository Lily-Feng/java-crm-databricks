package com.minicrm.persistence.jdbc;

import java.sql.SQLException;

/** Repository ports (§4) declare no checked exceptions; every adapter wraps {@link SQLException} here. */
public final class UncheckedSqlException extends RuntimeException {

    public UncheckedSqlException(SQLException cause) {
        super(cause);
    }
}
