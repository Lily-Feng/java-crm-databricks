package com.minicrm.application.analytics;

import com.minicrm.domain.shared.Money;

import java.util.Map;

/**
 * Backs {@code GET /dashboard/pipeline} (§3): "Pipeline totals by stage/owner/currency
 * from the selected adapter." {@code source}/{@code dataAsOf} matter once the Warehouse
 * analytics adapter exists (§5.3, Milestone 10); until then every value is computed
 * fresh from the local adapter, so both are fixed markers.
 */
public record PipelineSummary(
        Map<StageCurrency, Money> totalsByStage,
        Map<OwnerCurrency, Money> totalsByOwner,
        String source,
        java.time.Instant dataAsOf) {

    public PipelineSummary {
        totalsByStage = Map.copyOf(totalsByStage);
        totalsByOwner = Map.copyOf(totalsByOwner);
    }
}
