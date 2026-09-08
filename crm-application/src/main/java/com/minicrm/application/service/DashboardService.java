package com.minicrm.application.service;

import com.minicrm.application.analytics.OpportunityAnalyticsStreams;
import com.minicrm.application.analytics.PipelineSummary;
import com.minicrm.application.port.OpportunityRepository;

import java.time.Clock;
import java.util.Objects;

/**
 * Backs {@code GET /dashboard/pipeline} (§3). Reads local Postgres directly until the
 * Warehouse analytics adapter exists (§5.3): {@code source} is fixed at {@code "local"}
 * here and becomes adapter-selected once that later adapter is wired in at Milestone 10.
 */
public final class DashboardService {

    private final OpportunityRepository opportunities;
    private final Clock clock;

    public DashboardService(OpportunityRepository opportunities, Clock clock) {
        this.opportunities = Objects.requireNonNull(opportunities, "opportunities must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public PipelineSummary pipelineSummary() {
        var all = opportunities.findAll();
        return new PipelineSummary(
                OpportunityAnalyticsStreams.totalsByStage(all),
                OpportunityAnalyticsStreams.revenueByOwner(all),
                "local",
                clock.instant());
    }
}
