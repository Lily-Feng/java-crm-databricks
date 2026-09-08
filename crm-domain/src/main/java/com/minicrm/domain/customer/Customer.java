package com.minicrm.domain.customer;

import com.minicrm.domain.shared.CustomerId;
import com.minicrm.domain.shared.EmailAddress;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root (§3). Contacts, Activities and Opportunities are separate aggregates
 * keyed by {@link CustomerId} (§5.1's normalized tables) and are composed into a 360 view
 * at the application layer, not embedded here. Tags have no identity of their own, so they
 * are owned directly by the customer.
 */
public final class Customer {

    private final CustomerId id;
    private String name;
    private String industry;
    private EmailAddress email;
    private final Instant createdAt;
    private Instant updatedAt;
    private int version;
    private final Set<String> tags = new LinkedHashSet<>();

    public Customer(
            CustomerId id,
            String name,
            String industry,
            EmailAddress email,
            Instant createdAt,
            Instant updatedAt,
            int version,
            Set<String> tags) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = requireNonBlank(name, "name");
        this.industry = industry == null ? "" : industry;
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative: " + version);
        }
        this.version = version;
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public static Customer create(CustomerId id, String name, String industry, EmailAddress email, Instant now) {
        return new Customer(id, name, industry, email, now, now, 0, Set.of());
    }

    public void addTag(String tag) {
        tags.add(requireNonBlank(tag, "tag"));
    }

    public void removeTag(String tag) {
        tags.remove(tag);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public CustomerId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String industry() {
        return industry;
    }

    public EmailAddress email() {
        return email;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public int version() {
        return version;
    }

    public Set<String> tags() {
        return Collections.unmodifiableSet(tags);
    }
}
