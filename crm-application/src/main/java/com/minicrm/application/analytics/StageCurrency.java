package com.minicrm.application.analytics;

import com.minicrm.domain.opportunity.Stage;

import java.util.Currency;
import java.util.Objects;

public record StageCurrency(Stage stage, Currency currency) {

    public StageCurrency {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }
}
