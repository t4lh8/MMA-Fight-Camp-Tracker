package io.github.t4lh8.fightcamp.auth;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.t4lh8.fightcamp.audit.AuditLog;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class MeController {

    public record Profile(String username, Role role, WeightClass weightClass, OffsetDateTime createdAt) {
    }

    public record WeightClassRequest(WeightClass weightClass) {
    }

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final AuditLog audit;

    public MeController(CurrentUser currentUser, UserRepository users, AuditLog audit) {
        this.currentUser = currentUser;
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/api/me")
    public Profile me(@AuthenticationPrincipal Jwt jwt) {
        AppUser u = currentUser.require(jwt);
        return new Profile(u.username(), u.role(), u.weightClass(), u.createdAt());
    }

    @PutMapping("/api/me/weight-class")
    public Profile weightClass(@AuthenticationPrincipal Jwt jwt, @RequestBody WeightClassRequest req) {
        AppUser u = currentUser.require(jwt);
        users.updateWeightClass(u.id(), u.role() == Role.ATHLETE ? req.weightClass() : null);
        return me(jwt);
    }

    /** The user's own login history, so they can spot logins that were not them. */
    @GetMapping("/api/me/audit")
    public List<AuditLog.Event> audit(@AuthenticationPrincipal Jwt jwt) {
        AppUser u = currentUser.require(jwt);
        return audit.recent(u.username(), u.createdAt(), 20);
    }

    /** Deletes the account and all training data. Audit rows are kept as a security record. */
    @DeleteMapping("/api/me")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        AppUser u = currentUser.require(jwt);
        users.delete(u.id());
        audit.record(u.username(), "ACCOUNT_DELETED", null, http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
