package com.firehome;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * When each app was last on screen, from the system's usage statistics. install.sh allows the read with
 * `appops set ... GET_USAGE_STATS allow`; without it the map is empty and the order falls back to the names.
 */
final class RecentApps {
    private static final long WINDOW_MS = 30L * 24 * 60 * 60 * 1000;

    private final UsageStatsManager usage;

    RecentApps(Context context) {
        usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    /** Package name to the time (ms since the epoch) it was last in the foreground. */
    Map<String, Long> lastUsed() {
        Map<String, Long> lastUsed = new HashMap<>();
        if (usage == null) {
            return lastUsed;
        }
        long now = System.currentTimeMillis();
        Map<String, UsageStats> stats = usage.queryAndAggregateUsageStats(now - WINDOW_MS, now);
        if (stats != null) {
            for (Map.Entry<String, UsageStats> entry : stats.entrySet()) {
                lastUsed.put(entry.getKey(), entry.getValue().getLastTimeUsed());
            }
        }
        return lastUsed;
    }
}
