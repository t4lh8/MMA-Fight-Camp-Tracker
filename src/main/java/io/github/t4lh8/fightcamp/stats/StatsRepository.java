package io.github.t4lh8.fightcamp.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Aggregations done in SQL. All values are bound as parameters, nothing is concatenated into the query. */
@Repository
public class StatsRepository {

    public record WeekRow(LocalDate weekStart, int sessions, int minutes, long load) {
    }

    public record DisciplineRow(String discipline, int sessions, int minutes) {
    }

    public record RecordRow(int wins, int losses, int draws, int noContests, int koWins, int subWins,
                            int decisionWins) {
    }

    public record LatestWeight(LocalDate date, BigDecimal kg) {
    }

    private final JdbcClient jdbc;

    public StatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Load in the 7 and 28 days up to and including {@code today}. */
    public long[] loadWindows(long ownerId, LocalDate today) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(duration_min * rpe) FILTER (WHERE session_date > CAST(:today AS date) - 7), 0) AS acute,
                               COALESCE(SUM(duration_min * rpe), 0) AS load28
                        FROM training_session
                        WHERE owner_id = :owner
                          AND session_date <= CAST(:today AS date)
                          AND session_date > CAST(:today AS date) - 28""")
                .param("owner", ownerId)
                .param("today", today)
                .query((rs, i) -> new long[] {rs.getLong("acute"), rs.getLong("load28")})
                .single();
    }

    public List<WeekRow> weeklyLoad(long ownerId, LocalDate fromWeek) {
        return jdbc.sql("""
                        SELECT week_start, sessions, minutes, load FROM weekly_load
                        WHERE owner_id = :owner AND week_start >= CAST(:from AS date)
                        ORDER BY week_start""")
                .param("owner", ownerId)
                .param("from", fromWeek)
                .query((rs, i) -> new WeekRow(rs.getObject("week_start", LocalDate.class),
                        rs.getInt("sessions"), rs.getInt("minutes"), rs.getLong("load")))
                .list();
    }

    public List<DisciplineRow> disciplines(long ownerId, LocalDate from) {
        return jdbc.sql("""
                        SELECT discipline, COUNT(*) AS sessions, SUM(duration_min) AS minutes
                        FROM training_session
                        WHERE owner_id = :owner AND session_date >= CAST(:from AS date)
                        GROUP BY discipline
                        ORDER BY minutes DESC, discipline""")
                .param("owner", ownerId)
                .param("from", from)
                .query((rs, i) -> new DisciplineRow(rs.getString("discipline"), rs.getInt("sessions"),
                        rs.getInt("minutes")))
                .list();
    }

    public RecordRow record(long ownerId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FILTER (WHERE result = 'WIN')                         AS wins,
                               COUNT(*) FILTER (WHERE result = 'LOSS')                        AS losses,
                               COUNT(*) FILTER (WHERE result = 'DRAW')                        AS draws,
                               COUNT(*) FILTER (WHERE result = 'NC')                          AS nc,
                               COUNT(*) FILTER (WHERE result = 'WIN' AND method = 'KO_TKO')     AS ko,
                               COUNT(*) FILTER (WHERE result = 'WIN' AND method = 'SUBMISSION') AS sub,
                               COUNT(*) FILTER (WHERE result = 'WIN' AND method = 'DECISION')   AS dec
                        FROM fight WHERE owner_id = :owner""")
                .param("owner", ownerId)
                .query((rs, i) -> new RecordRow(rs.getInt("wins"), rs.getInt("losses"), rs.getInt("draws"),
                        rs.getInt("nc"), rs.getInt("ko"), rs.getInt("sub"), rs.getInt("dec")))
                .single();
    }

    /** Results from newest to oldest, used for the current streak. */
    public List<String> resultsNewestFirst(long ownerId) {
        return jdbc.sql("SELECT result FROM fight WHERE owner_id = :owner ORDER BY fight_date DESC, id DESC")
                .param("owner", ownerId)
                .query(String.class)
                .list();
    }

    public Optional<LatestWeight> latestWeight(long ownerId) {
        return jdbc.sql("""
                        SELECT weigh_date, kg FROM weigh_in WHERE owner_id = :owner
                        ORDER BY weigh_date DESC LIMIT 1""")
                .param("owner", ownerId)
                .query((rs, i) -> new LatestWeight(rs.getObject("weigh_date", LocalDate.class), rs.getBigDecimal("kg")))
                .optional();
    }
}
