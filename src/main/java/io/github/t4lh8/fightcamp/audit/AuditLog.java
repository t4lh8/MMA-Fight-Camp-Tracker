package io.github.t4lh8.fightcamp.audit;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditLog {

    public record Event(OffsetDateTime at, String action, String detail, String clientIp) {
    }

    private final JdbcClient jdbc;

    public AuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String username, String action, String detail, String clientIp) {
        jdbc.sql("""
                        INSERT INTO audit_event (username, action, detail, client_ip)
                        VALUES (:username, :action, :detail, :ip)""")
                .param("username", truncate(username, 32))
                .param("action", action)
                .param("detail", truncate(detail, 200))
                .param("ip", truncate(clientIp, 45))
                .update();
    }

    /** Only events since {@code since}, so a new account that reuses a deleted username cannot see the old history. */
    public List<Event> recent(String username, OffsetDateTime since, int limit) {
        return jdbc.sql("""
                        SELECT occurred_at, action, detail, client_ip FROM audit_event
                        WHERE username = :username AND occurred_at >= :since ORDER BY occurred_at DESC, id DESC LIMIT :limit""")
                .param("username", username)
                .param("since", since)
                .param("limit", limit)
                .query((rs, i) -> new Event(rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("action"), rs.getString("detail"), rs.getString("client_ip")))
                .list();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
