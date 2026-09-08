package com.minicrm.domain.shared;

import java.util.Objects;

/**
 * Teaching-level validation only (§3): presence of "@". Not a full RFC 5322 validator.
 */
public record EmailAddress(String value) {

    public EmailAddress {
        Objects.requireNonNull(value, "email must not be null");
        if (!value.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
    }
}
