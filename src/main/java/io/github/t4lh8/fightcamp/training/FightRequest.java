package io.github.t4lh8.fightcamp.training;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record FightRequest(
        @NotNull @PastOrPresent LocalDate date,
        @NotBlank @Size(max = 80) String opponent,
        @Size(max = 120) String eventName,
        @NotNull Fight.Result result,
        @NotNull Fight.Method method,
        @NotNull @Min(1) @Max(5) Integer endRound) {

    /** Same rule as the CHECK constraint in the schema, so the client gets a 400 instead of a 500. */
    @JsonIgnore
    @AssertTrue(message = "a draw or no contest must end by decision or other")
    public boolean isMethodConsistent() {
        if (result == null || method == null) {
            return true;
        }
        boolean finish = method == Fight.Method.KO_TKO || method == Fight.Method.SUBMISSION;
        return !finish || result == Fight.Result.WIN || result == Fight.Result.LOSS;
    }
}
