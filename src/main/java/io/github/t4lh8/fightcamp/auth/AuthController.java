package io.github.t4lh8.fightcamp.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.t4lh8.fightcamp.audit.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String USERNAME_PATTERN = "^[a-zA-Z0-9_.-]{3,32}$";
    private static final String BAD_LOGIN = "Invalid username or password";

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = USERNAME_PATTERN, message = "3-32 letters, digits, dot, dash or underscore")
            String username,
            @NotNull @Size(max = 128) String password,
            @NotNull Role role,
            WeightClass weightClass) {
    }

    public record LoginRequest(@NotBlank @Size(max = 32) String username, @NotBlank @Size(max = 128) String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, String username, Role role) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final LoginThrottle throttle;
    private final TokenService tokens;
    private final AuditLog audit;
    // Compared against when the username does not exist, so both paths take about the same time.
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy,
                          LoginThrottle throttle, TokenService tokens, AuditLog audit) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.throttle = throttle;
        this.tokens = tokens;
        this.audit = audit;
        this.dummyHash = encoder.encode("timing-equaliser-not-a-real-password");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        List<String> problems = policy.problems(req.username(), req.password());
        if (!problems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Validation failed",
                    "fields", Map.of("password", String.join(", ", problems))));
        }
        WeightClass wc = req.role() == Role.ATHLETE ? req.weightClass() : null;
        try {
            users.insert(req.username(), encoder.encode(req.password()), req.role(), wc);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username is taken"));
        }
        audit.record(req.username(), "REGISTER", req.role().name(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", req.username()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        var locked = throttle.lockedFor(req.username());
        if (locked.isPresent()) {
            audit.record(req.username(), "LOGIN_LOCKED", null, ip);
            long seconds = Math.max(1, locked.get().toSeconds());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                    .body(Map.of("error", "Too many failed attempts. Try again in "
                            + ((seconds + 59) / 60) + " min"));
        }

        var user = users.findByUsername(req.username());
        boolean ok = encoder.matches(req.password(), user.map(AppUser::passwordHash).orElse(dummyHash))
                && user.isPresent();
        if (!ok) {
            throttle.recordFailure(req.username());
            audit.record(req.username(), "LOGIN_FAIL", null, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", BAD_LOGIN));
        }

        throttle.reset(req.username());
        audit.record(req.username(), "LOGIN_OK", null, ip);
        var issued = tokens.issue(user.get());
        return ResponseEntity.ok(new LoginResponse(issued.token(), issued.expiresAt(),
                user.get().username(), user.get().role()));
    }
}
