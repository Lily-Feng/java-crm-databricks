package com.minicrm.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record ContactId(UUID value) {

    public ContactId {
        Objects.requireNonNull(value, "ContactId value must not be null");
    }

    public static ContactId newId() {
        return new ContactId(UUID.randomUUID());
    }

    public static ContactId of(String value) {
        return new ContactId(UUID.fromString(value));
    }
}
