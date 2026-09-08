package com.minicrm.application.analytics;

import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.shared.CustomerId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActivityAnalyticsLoops {

    private ActivityAnalyticsLoops() {
    }

    public static Map<CustomerId, Long> activitiesByCustomer(List<Activity> activities) {
        Map<CustomerId, Long> counts = new LinkedHashMap<>();
        for (Activity activity : activities) {
            counts.merge(activity.customerId(), 1L, Long::sum);
        }
        return counts;
    }
}
