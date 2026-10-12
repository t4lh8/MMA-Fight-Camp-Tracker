package io.github.t4lh8.fightcamp.training;

import java.time.LocalDate;

public record Fight(long id, LocalDate date, String opponent, String eventName, Result result,
                    Method method, int endRound) {

    public enum Result { WIN, LOSS, DRAW, NC }

    public enum Method { KO_TKO, SUBMISSION, DECISION, OTHER }
}
