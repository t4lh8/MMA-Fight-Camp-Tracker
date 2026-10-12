package io.github.t4lh8.fightcamp.auth;

import java.math.BigDecimal;

/** Unified MMA weight class limits, converted from pounds to kilograms. */
public enum WeightClass {
    STRAWWEIGHT("52.2"),
    FLYWEIGHT("56.7"),
    BANTAMWEIGHT("61.2"),
    FEATHERWEIGHT("65.8"),
    LIGHTWEIGHT("70.3"),
    WELTERWEIGHT("77.1"),
    MIDDLEWEIGHT("83.9"),
    LIGHT_HEAVYWEIGHT("93.0"),
    HEAVYWEIGHT("120.2");

    private final BigDecimal limitKg;

    WeightClass(String limitKg) {
        this.limitKg = new BigDecimal(limitKg);
    }

    public BigDecimal limitKg() {
        return limitKg;
    }
}
