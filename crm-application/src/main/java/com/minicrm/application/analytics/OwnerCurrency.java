package com.minicrm.application.analytics;

import java.util.Currency;
import java.util.Objects;

/** Grouping key: revenue is only meaningful summed within one currency (§3). */
public record OwnerCurrency(String owner, Currency currency) {

    public OwnerCurrency {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }
}
