package io.github.t4lh8.fightcamp.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import io.github.t4lh8.fightcamp.auth.WeightClass;

/** How far the latest weigh-in is above the weight class limit. The thresholds are my own simple choice, not medical advice. */
public record WeightCut(WeightClass weightClass, BigDecimal limitKg, BigDecimal latestKg, LocalDate latestDate,
                        BigDecimal kgToCut, BigDecimal percentOfBodyweight, Status status) {

    public enum Status { ON_WEIGHT, MANAGEABLE, HEAVY, EXTREME }

    public static WeightCut of(WeightClass wc, BigDecimal latestKg, LocalDate latestDate) {
        BigDecimal limit = wc.limitKg();
        BigDecimal toCut = latestKg.subtract(limit).max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
        BigDecimal percent = toCut.multiply(BigDecimal.valueOf(100))
                .divide(latestKg, 1, RoundingMode.HALF_UP);
        Status status;
        if (toCut.signum() == 0) {
            status = Status.ON_WEIGHT;
        } else if (percent.compareTo(BigDecimal.valueOf(5)) <= 0) {
            status = Status.MANAGEABLE;
        } else if (percent.compareTo(BigDecimal.valueOf(8)) <= 0) {
            status = Status.HEAVY;
        } else {
            status = Status.EXTREME;
        }
        return new WeightCut(wc, limit, latestKg, latestDate, toCut, percent, status);
    }
}
