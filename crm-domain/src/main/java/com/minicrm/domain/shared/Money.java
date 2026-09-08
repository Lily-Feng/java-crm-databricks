package com.minicrm.domain.shared;

import java.util.Currency;
import java.util.Objects;

/**
 * Currency plus integer minor units (§3). Never use {@code double} for money.
 * Different currencies are never implicitly combined: {@link #plus(Money)} rejects
 * a currency mismatch rather than silently summing incompatible amounts.
 */
public record Money(Currency currency, long minorUnits) {

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
        if (minorUnits < 0) {
            throw new IllegalArgumentException("minorUnits must not be negative: " + minorUnits);
        }
    }

    public static Money of(String currencyCode, long minorUnits) {
        return new Money(Currency.getInstance(currencyCode), minorUnits);
    }

    public static Money zero(String currencyCode) {
        return Money.of(currencyCode, 0L);
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot combine different currencies: " + currency + " and " + other.currency);
        }
        return new Money(currency, Math.addExact(minorUnits, other.minorUnits));
    }
}
