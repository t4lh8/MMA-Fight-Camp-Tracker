package io.github.t4lh8.fightcamp.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import io.github.t4lh8.fightcamp.common.AppProperties;

/**
 * Locks an account for the rest of the window after too many failed logins.
 * Keyed on username only: client IPs are easy to spoof behind proxies.
 * In-memory, so it is per instance. A shared store would be needed when scaling out.
 */
@Component
public class LoginThrottle {

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    public LoginThrottle(AppProperties props, Clock clock) {
        this.maxFailures = props.login().maxFailures();
        this.window = props.login().window();
        this.clock = clock;
    }

    /** Time left until the account unlocks, or empty if login attempts are allowed. */
    public Optional<Duration> lockedFor(String username) {
        Deque<Instant> attempts = failures.get(key(username));
        if (attempts == null) {
            return Optional.empty();
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() < maxFailures) {
                return Optional.empty();
            }
            Duration left = Duration.between(clock.instant(), attempts.peekFirst().plus(window));
            return Optional.of(left.isNegative() ? Duration.ZERO : left);
        }
    }

    public void recordFailure(String username) {
        Deque<Instant> attempts = failures.computeIfAbsent(key(username), k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    public void reset(String username) {
        failures.remove(key(username));
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }
}
