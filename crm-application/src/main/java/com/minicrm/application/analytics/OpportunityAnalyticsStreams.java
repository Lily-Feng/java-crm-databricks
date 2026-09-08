package com.minicrm.application.analytics;

import com.minicrm.domain.opportunity.Opportunity;
import com.minicrm.domain.opportunity.Stage;
import com.minicrm.domain.shared.Money;

import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Same four aggregates as {@link OpportunityAnalyticsLoops}, expressed with the Stream
 * API (idea.md §11: map/filter/collect/groupingBy/Collectors). Deliberately sequential —
 * idea.md §11's {@code parallelStream()}-over-blocking-repositories lesson is a separate,
 * later lab (design-spec §7), not something to reach for here by default.
 */
public final class OpportunityAnalyticsStreams {

    private OpportunityAnalyticsStreams() {
    }

    public static Map<OwnerCurrency, Money> revenueByOwner(List<Opportunity> opportunities) {
        Map<OwnerCurrency, java.util.Optional<Money>> summed = opportunities.stream()
                .collect(Collectors.groupingBy(
                        o -> new OwnerCurrency(o.owner(), o.amount().currency()),
                        Collectors.mapping(Opportunity::amount, Collectors.reducing(Money::plus))));
        return summed.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().orElseThrow()));
    }

    public static Map<Currency, Long> averageOpportunitySizeMinorUnits(List<Opportunity> opportunities) {
        // Integer division, matching OpportunityAnalyticsLoops exactly (sum / count),
        // rather than averagingLong's double average: two implementations of the same
        // aggregate are only useful as a lesson if they're actually comparable.
        Map<Currency, Long> sums = opportunities.stream()
                .collect(Collectors.groupingBy(o -> o.amount().currency(),
                        Collectors.summingLong(o -> o.amount().minorUnits())));
        Map<Currency, Long> counts = opportunities.stream()
                .collect(Collectors.groupingBy(o -> o.amount().currency(), Collectors.counting()));
        return sums.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / counts.get(e.getKey())));
    }

    public static List<Opportunity> topOpportunities(List<Opportunity> opportunities, Currency currency, int n) {
        return opportunities.stream()
                .filter(o -> o.amount().currency().equals(currency))
                .sorted(Comparator.comparingLong((Opportunity o) -> o.amount().minorUnits()).reversed())
                .limit(n)
                .toList();
    }

    public static Map<Stage, Long> conversionByStage(List<Opportunity> opportunities) {
        return opportunities.stream()
                .collect(Collectors.groupingBy(Opportunity::stage, Collectors.counting()));
    }

    /** Same grouped-sum shape as {@link #revenueByOwner}, keyed by stage instead of owner. */
    public static Map<StageCurrency, Money> totalsByStage(List<Opportunity> opportunities) {
        Map<StageCurrency, java.util.Optional<Money>> summed = opportunities.stream()
                .collect(Collectors.groupingBy(
                        o -> new StageCurrency(o.stage(), o.amount().currency()),
                        Collectors.mapping(Opportunity::amount, Collectors.reducing(Money::plus))));
        return summed.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().orElseThrow()));
    }
}
