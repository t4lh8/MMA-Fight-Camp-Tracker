package io.github.t4lh8.fightcamp.training;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeighIn(long id, LocalDate date, BigDecimal kg) {
}
