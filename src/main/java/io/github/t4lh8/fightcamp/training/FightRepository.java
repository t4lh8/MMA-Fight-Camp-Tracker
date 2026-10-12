package io.github.t4lh8.fightcamp.training;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FightRepository {

    private final JdbcClient jdbc;

    public FightRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Fight> list(long ownerId) {
        return jdbc.sql("SELECT * FROM fight WHERE owner_id = :owner ORDER BY fight_date DESC, id DESC")
                .param("owner", ownerId)
                .query((rs, i) -> new Fight(
                        rs.getLong("id"),
                        rs.getObject("fight_date", LocalDate.class),
                        rs.getString("opponent"),
                        rs.getString("event_name"),
                        Fight.Result.valueOf(rs.getString("result")),
                        Fight.Method.valueOf(rs.getString("method")),
                        rs.getInt("end_round")))
                .list();
    }

    public long insert(long ownerId, FightRequest r) {
        return jdbc.sql("""
                        INSERT INTO fight (owner_id, fight_date, opponent, event_name, result, method, end_round)
                        VALUES (:owner, :date, :opponent, :event, :result, :method, :round)
                        RETURNING id""")
                .param("owner", ownerId)
                .param("date", r.date())
                .param("opponent", r.opponent().strip())
                .param("event", TrainingRepository.blankToNull(r.eventName()))
                .param("result", r.result().name())
                .param("method", r.method().name())
                .param("round", r.endRound())
                .query(Long.class)
                .single();
    }

    public boolean delete(long ownerId, long id) {
        return jdbc.sql("DELETE FROM fight WHERE id = :id AND owner_id = :owner")
                .param("id", id)
                .param("owner", ownerId)
                .update() == 1;
    }
}
