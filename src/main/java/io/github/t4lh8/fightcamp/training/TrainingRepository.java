package io.github.t4lh8.fightcamp.training;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Every query is scoped to owner_id, so one athlete can never read or change another athlete's rows. */
@Repository
public class TrainingRepository {

    private final JdbcClient jdbc;

    public TrainingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<TrainingSession> list(long ownerId, int limit) {
        return jdbc.sql("""
                        SELECT * FROM training_session WHERE owner_id = :owner
                        ORDER BY session_date DESC, id DESC LIMIT :limit""")
                .param("owner", ownerId)
                .param("limit", limit)
                .query((rs, i) -> new TrainingSession(
                        rs.getLong("id"),
                        rs.getObject("session_date", LocalDate.class),
                        Discipline.valueOf(rs.getString("discipline")),
                        rs.getInt("duration_min"),
                        rs.getInt("rpe"),
                        (Integer) rs.getObject("rounds"),
                        rs.getString("notes")))
                .list();
    }

    public long insert(long ownerId, SessionRequest r) {
        return jdbc.sql("""
                        INSERT INTO training_session (owner_id, session_date, discipline, duration_min, rpe, rounds, notes)
                        VALUES (:owner, :date, :discipline, :duration, :rpe, :rounds, :notes)
                        RETURNING id""")
                .param("owner", ownerId)
                .param("date", r.date())
                .param("discipline", r.discipline().name())
                .param("duration", r.durationMin())
                .param("rpe", r.rpe())
                .param("rounds", r.rounds())
                .param("notes", blankToNull(r.notes()))
                .query(Long.class)
                .single();
    }

    public boolean delete(long ownerId, long id) {
        return jdbc.sql("DELETE FROM training_session WHERE id = :id AND owner_id = :owner")
                .param("id", id)
                .param("owner", ownerId)
                .update() == 1;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
