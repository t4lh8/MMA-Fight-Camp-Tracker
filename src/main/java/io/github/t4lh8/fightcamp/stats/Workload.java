package io.github.t4lh8.fightcamp.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Acute:chronic workload ratio. Acute = load in the last 7 days, chronic = average weekly
 * load over the last 28 days. A sudden jump well above what the athlete is used to is a
 * common warning sign for overuse injuries.
 */
public record Workload(long acute, long chronicWeekly, BigDecimal ratio, Status status) {

    public enum Status { NO_BASELINE, UNDERLOADED, SWEET_SPOT, CAUTION, SPIKE }

    public static Workload of(long acute7, long load28) {
        long chronic = Math.round(load28 / 4.0);
        if (chronic == 0) {
            return new Workload(acute7, 0, null, Status.NO_BASELINE);
        }
        BigDecimal ratio = BigDecimal.valueOf(acute7).divide(BigDecimal.valueOf(chronic), 2, RoundingMode.HALF_UP);
        return new Workload(acute7, chronic, ratio, statusFor(ratio));
    }

    static Status statusFor(BigDecimal ratio) {
        double r = ratio.doubleValue();
        if (r < 0.8) {
            return Status.UNDERLOADED;
        }
        if (r <= 1.3) {
            return Status.SWEET_SPOT;
        }
        if (r <= 1.5) {
            return Status.CAUTION;
        }
        return Status.SPIKE;
    }
}
