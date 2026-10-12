package io.github.t4lh8.fightcamp.training;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

public record WeighInRequest(
        @NotNull @PastOrPresent LocalDate date,
        @NotNull @DecimalMin("40.0") @DecimalMax("200.0") @Digits(integer = 3, fraction = 2) BigDecimal kg) {
}
