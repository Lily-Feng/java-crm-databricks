package com.minicrm.application.analytics;

import com.minicrm.domain.activity.Activity;
import com.minicrm.domain.shared.CustomerId;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ActivityAnalyticsStreams {

    private ActivityAnalyticsStreams() {
    }

    public static Map<CustomerId, Long> activitiesByCustomer(List<Activity> activities) {
        return activities.stream()
                .collect(Collectors.groupingBy(Activity::customerId, Collectors.counting()));
    }
}
