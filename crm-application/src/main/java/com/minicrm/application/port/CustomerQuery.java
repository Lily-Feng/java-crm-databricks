package com.minicrm.application.port;

import java.util.Objects;
import java.util.Optional;

/**
 * §3: "Bounded search results; pagination/limit documented." {@code limit} is capped
 * here so a caller cannot accidentally request an unbounded scan through a repository
 * port, regardless of which adapter (in-memory, Postgres, Lakebase) answers it.
 */
public record CustomerQuery(Optional<String> search, int limit) {

    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 20;

    public CustomerQuery {
        Objects.requireNonNull(search, "search must not be null (use Optional.empty())");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be within [1, %d]: %d".formatted(MAX_LIMIT, limit));
        }
    }

    public static CustomerQuery of(String search) {
        return new CustomerQuery(Optional.ofNullable(search).filter(s -> !s.isBlank()), DEFAULT_LIMIT);
    }
}
