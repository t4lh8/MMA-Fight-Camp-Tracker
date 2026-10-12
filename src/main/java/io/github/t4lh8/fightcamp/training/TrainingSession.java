package io.github.t4lh8.fightcamp.training;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrainingSession(long id, LocalDate date, Discipline discipline, int durationMin, int rpe,
                              Integer rounds, String notes) {

    /** Session RPE load (Foster): minutes multiplied by perceived effort. */
    @JsonProperty("load")
    public int load() {
        return durationMin * rpe;
    }
}
