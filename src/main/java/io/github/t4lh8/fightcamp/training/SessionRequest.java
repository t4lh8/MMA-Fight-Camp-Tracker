package io.github.t4lh8.fightcamp.training;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record SessionRequest(
        @NotNull @PastOrPresent LocalDate date,
        @NotNull Discipline discipline,
        @NotNull @Min(1) @Max(480) Integer durationMin,
        @NotNull @Min(1) @Max(10) Integer rpe,
        @Min(0) @Max(50) Integer rounds,
        @Size(max = 500) String notes) {
}
