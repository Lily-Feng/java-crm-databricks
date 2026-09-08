package com.minicrm.application.analytics;

import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.shared.Money;

import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * idea.md §11: "Implement them with collections first: for (...)". The loop and
 * {@link OpportunityAnalyticsStreams} implementations are asserted equal on the same
 * fixture data (idea.md §11's loops-vs-Streams review), not just described as
 * equivalent.
 */
public final class OpportunityAnalyticsLoops {

    private OpportunityAnalyticsLoops() {
    }

    public static Map<OwnerCurrency, Money> revenueByOwner(List<Opportunity> opportunities) {
        Map<OwnerCurrency, Money> totals = new LinkedHashMap<>();
        for (Opportunity opportunity : opportunities) {
            var key = new OwnerCurrency(opportunity.owner(), opportunity.amount().currency());
            var running = totals.get(key);
            totals.put(key, running == null ? opportunity.amount() : running.plus(opportunity.amount()));
        }
        return totals;
    }

    public static Map<Currency, Long> averageOpportunitySizeMinorUnits(List<Opportunity> opportunities) {
        Map<Currency, Long> sums = new LinkedHashMap<>();
        Map<Currency, Long> counts = new LinkedHashMap<>();
        for (Opportunity opportunity : opportunities) {
            var currency = opportunity.amount().currency();
            sums.merge(currency, opportunity.amount().minorUnits(), Long::sum);
            counts.merge(currency, 1L, Long::sum);
        }
        Map<Currency, Long> averages = new LinkedHashMap<>();
        for (var entry : sums.entrySet()) {
            averages.put(entry.getKey(), entry.getValue() / counts.get(entry.getKey()));
        }
        return averages;
    }

    public static List<Opportunity> topOpportunities(List<Opportunity> opportunities, Currency currency, int n) {
        List<Opportunity> inCurrency = new ArrayList<>();
        for (Opportunity opportunity : opportunities) {
            if (opportunity.amount().currency().equals(currency)) {
                inCurrency.add(opportunity);
            }
        }
        inCurrency.sort((a, b) -> Long.compare(b.amount().minorUnits(), a.amount().minorUnits()));
        return inCurrency.size() > n ? inCurrency.subList(0, n) : inCurrency;
    }

    public static Map<Stage, Long> conversionByStage(List<Opportunity> opportunities) {
        Map<Stage, Long> counts = new LinkedHashMap<>();
        for (Opportunity opportunity : opportunities) {
            counts.merge(opportunity.stage(), 1L, Long::sum);
        }
        return counts;
    }
}
