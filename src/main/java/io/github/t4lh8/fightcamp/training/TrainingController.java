package io.github.t4lh8.fightcamp.training;

import java.net.URI;
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

import io.github.t4lh8.fightcamp.auth.CurrentUser;
import io.github.t4lh8.fightcamp.common.NotFoundException;
import jakarta.validation.Valid;

/** Sessions, fights and weigh-ins for the logged-in athlete. The owner always comes from the token. */
@RestController
public class TrainingController {

    private final TrainingRepository sessions;
    private final FightRepository fights;
    private final WeighInRepository weighIns;
    private final CurrentUser currentUser;

    public TrainingController(TrainingRepository sessions, FightRepository fights,
                              WeighInRepository weighIns, CurrentUser currentUser) {
        this.sessions = sessions;
        this.fights = fights;
        this.weighIns = weighIns;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/sessions")
    public List<TrainingSession> sessions(@AuthenticationPrincipal Jwt jwt) {
        return sessions.list(currentUser.id(jwt), 200);
    }

    @PostMapping("/api/sessions")
    public ResponseEntity<Map<String, Long>> addSession(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody SessionRequest req) {
        long id = sessions.insert(currentUser.id(jwt), req);
        return ResponseEntity.created(URI.create("/api/sessions/" + id)).body(Map.of("id", id));
    }

    @DeleteMapping("/api/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return deleted(sessions.delete(currentUser.id(jwt), id));
    }

    @GetMapping("/api/fights")
    public List<Fight> fights(@AuthenticationPrincipal Jwt jwt) {
        return fights.list(currentUser.id(jwt));
    }

    @PostMapping("/api/fights")
    public ResponseEntity<Map<String, Long>> addFight(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody FightRequest req) {
        long id = fights.insert(currentUser.id(jwt), req);
        return ResponseEntity.created(URI.create("/api/fights/" + id)).body(Map.of("id", id));
    }

    @DeleteMapping("/api/fights/{id}")
    public ResponseEntity<Void> deleteFight(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return deleted(fights.delete(currentUser.id(jwt), id));
    }

    @GetMapping("/api/weigh-ins")
    public List<WeighIn> weighIns(@AuthenticationPrincipal Jwt jwt) {
        return weighIns.list(currentUser.id(jwt), 60);
    }

    @PostMapping("/api/weigh-ins")
    public ResponseEntity<Map<String, Long>> addWeighIn(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody WeighInRequest req) {
        long id = weighIns.upsert(currentUser.id(jwt), req.date(), req.kg());
        return ResponseEntity.created(URI.create("/api/weigh-ins/" + id)).body(Map.of("id", id));
    }

    @DeleteMapping("/api/weigh-ins/{id}")
    public ResponseEntity<Void> deleteWeighIn(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return deleted(weighIns.delete(currentUser.id(jwt), id));
    }

    /** Someone else's id gives the same 404 as a missing id, so ids cannot be probed. */
    private static ResponseEntity<Void> deleted(boolean deleted) {
        if (!deleted) {
            throw new NotFoundException();
        }
        return ResponseEntity.noContent().build();
    }
}
