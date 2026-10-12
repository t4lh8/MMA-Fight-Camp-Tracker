package io.github.t4lh8.fightcamp.coach;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.t4lh8.fightcamp.audit.AuditLog;
import io.github.t4lh8.fightcamp.auth.AppUser;
import io.github.t4lh8.fightcamp.auth.CurrentUser;
import io.github.t4lh8.fightcamp.auth.Role;
import io.github.t4lh8.fightcamp.auth.UserRepository;
import io.github.t4lh8.fightcamp.common.NotFoundException;
import io.github.t4lh8.fightcamp.stats.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Athletes share a read-only view with a coach. A coach without access gets the same 404
 * whether the athlete exists or not, so usernames cannot be enumerated through this endpoint.
 */
@RestController
public class CoachController {

    public record GrantRequest(@NotBlank @Size(max = 32) String coach) {
    }

    private final CoachRepository access;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final DashboardService dashboards;
    private final AuditLog audit;

    public CoachController(CoachRepository access, UserRepository users, CurrentUser currentUser,
                           DashboardService dashboards, AuditLog audit) {
        this.access = access;
        this.users = users;
        this.currentUser = currentUser;
        this.dashboards = dashboards;
        this.audit = audit;
    }

    // Athlete side

    @GetMapping("/api/me/coaches")
    public List<String> myCoaches(@AuthenticationPrincipal Jwt jwt) {
        return access.coachesOf(currentUser.id(jwt));
    }

    @PostMapping("/api/me/coaches")
    public ResponseEntity<Map<String, String>> grant(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody GrantRequest req, HttpServletRequest http) {
        AppUser me = currentUser.require(jwt);
        AppUser coach = users.findByUsername(req.coach())
                .filter(u -> u.role() == Role.COACH)
                .orElseThrow(NotFoundException::new);
        access.grant(me.id(), coach.id());
        audit.record(me.username(), "ACCESS_GRANTED", "coach " + coach.username(), http.getRemoteAddr());
        return ResponseEntity.ok(Map.of("coach", coach.username()));
    }

    @DeleteMapping("/api/me/coaches/{coach}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable String coach,
                                       HttpServletRequest http) {
        AppUser me = currentUser.require(jwt);
        AppUser c = users.findByUsername(coach).orElseThrow(NotFoundException::new);
        if (!access.revoke(me.id(), c.id())) {
            throw new NotFoundException();
        }
        audit.record(me.username(), "ACCESS_REVOKED", "coach " + c.username(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    // Coach side

    @GetMapping("/api/coach/athletes")
    public List<String> athletes(@AuthenticationPrincipal Jwt jwt) {
        return access.athletesOf(currentUser.id(jwt));
    }

    @GetMapping("/api/coach/athletes/{username}/dashboard")
    public DashboardService.Dashboard athleteDashboard(@AuthenticationPrincipal Jwt jwt, @PathVariable String username,
                                                       HttpServletRequest http) {
        AppUser coach = currentUser.require(jwt);
        AppUser athlete = users.findByUsername(username)
                .filter(a -> access.hasAccess(coach.id(), a.id()))
                .orElse(null);
        if (athlete == null) {
            audit.record(coach.username(), "ACCESS_DENIED", "dashboard of " + username, http.getRemoteAddr());
            throw new NotFoundException();
        }
        return dashboards.build(athlete);
    }
}
