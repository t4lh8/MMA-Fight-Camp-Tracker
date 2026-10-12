package io.github.t4lh8.fightcamp.stats;

import java.util.List;

/** Exposes package-private helpers to tests in other packages. */
public final class DashboardServiceAccess {

    private DashboardServiceAccess() {
    }

    public static String streak(List<String> newestFirst) {
        return DashboardService.streak(newestFirst);
    }
}
