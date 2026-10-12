package io.github.t4lh8.fightcamp.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByUsername(String username) {
        return jdbc.sql("SELECT * FROM app_user WHERE username = :username")
                .param("username", username)
                .query(UserRepository::map)
                .optional();
    }

    public Optional<AppUser> findById(long id) {
        return jdbc.sql("SELECT * FROM app_user WHERE id = :id")
                .param("id", id)
                .query(UserRepository::map)
                .optional();
    }

    public long insert(String username, String passwordHash, Role role, WeightClass weightClass) {
        return jdbc.sql("""
                        INSERT INTO app_user (username, password_hash, role, weight_class)
                        VALUES (:username, :hash, :role, :weightClass)
                        RETURNING id""")
                .param("username", username)
                .param("hash", passwordHash)
                .param("role", role.name())
                .param("weightClass", weightClass == null ? null : weightClass.name())
                .query(Long.class)
                .single();
    }

    public void updateWeightClass(long id, WeightClass weightClass) {
        jdbc.sql("UPDATE app_user SET weight_class = :wc WHERE id = :id")
                .param("wc", weightClass == null ? null : weightClass.name())
                .param("id", id)
                .update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", id).update();
    }

    static AppUser map(ResultSet rs, int row) throws SQLException {
        String wc = rs.getString("weight_class");
        return new AppUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                wc == null ? null : WeightClass.valueOf(wc),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
