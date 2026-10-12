package io.github.t4lh8.fightcamp.training;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WeighInRepository {

    private final JdbcClient jdbc;

    public WeighInRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<WeighIn> list(long ownerId, int limit) {
        return jdbc.sql("""
                        SELECT id, weigh_date, kg FROM weigh_in WHERE owner_id = :owner
                        ORDER BY weigh_date DESC LIMIT :limit""")
                .param("owner", ownerId)
                .param("limit", limit)
                .query((rs, i) -> new WeighIn(rs.getLong("id"), rs.getObject("weigh_date", LocalDate.class),
                        rs.getBigDecimal("kg")))
                .list();
    }

    /** One weigh-in per day. Logging the same day again replaces the earlier value. */
    public long upsert(long ownerId, LocalDate date, BigDecimal kg) {
        return jdbc.sql("""
                        INSERT INTO weigh_in (owner_id, weigh_date, kg) VALUES (:owner, :date, :kg)
                        ON CONFLICT (owner_id, weigh_date) DO UPDATE SET kg = EXCLUDED.kg
                        RETURNING id""")
                .param("owner", ownerId)
                .param("date", date)
                .param("kg", kg)
                .query(Long.class)
                .single();
    }

    public boolean delete(long ownerId, long id) {
        return jdbc.sql("DELETE FROM weigh_in WHERE id = :id AND owner_id = :owner")
                .param("id", id)
                .param("owner", ownerId)
                .update() == 1;
    }
}
