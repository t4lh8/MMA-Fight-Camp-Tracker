package io.github.t4lh8.fightcamp.coach;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CoachRepository {

    private final JdbcClient jdbc;

    public CoachRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void grant(long athleteId, long coachId) {
        jdbc.sql("""
                        INSERT INTO coach_access (athlete_id, coach_id) VALUES (:athlete, :coach)
                        ON CONFLICT DO NOTHING""")
                .param("athlete", athleteId)
                .param("coach", coachId)
                .update();
    }

    public boolean revoke(long athleteId, long coachId) {
        return jdbc.sql("DELETE FROM coach_access WHERE athlete_id = :athlete AND coach_id = :coach")
                .param("athlete", athleteId)
                .param("coach", coachId)
                .update() == 1;
    }

    public boolean hasAccess(long coachId, long athleteId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM coach_access WHERE athlete_id = :athlete AND coach_id = :coach)")
                .param("athlete", athleteId)
                .param("coach", coachId)
                .query(Boolean.class)
                .single();
    }

    public List<String> coachesOf(long athleteId) {
        return jdbc.sql("""
                        SELECT u.username FROM coach_access a JOIN app_user u ON u.id = a.coach_id
                        WHERE a.athlete_id = :athlete ORDER BY u.username""")
                .param("athlete", athleteId)
                .query(String.class)
                .list();
    }

    public List<String> athletesOf(long coachId) {
        return jdbc.sql("""
                        SELECT u.username FROM coach_access a JOIN app_user u ON u.id = a.athlete_id
                        WHERE a.coach_id = :coach ORDER BY u.username""")
                .param("coach", coachId)
                .query(String.class)
                .list();
    }
}
