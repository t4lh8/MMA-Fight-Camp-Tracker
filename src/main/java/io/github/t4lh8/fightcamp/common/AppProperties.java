package io.github.t4lh8.fightcamp.common;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fightcamp")
public record AppProperties(Jwt jwt, Login login) {

    public record Jwt(String secret, Duration ttl) {
        public Jwt {
            // Fail fast: a short or missing HMAC key makes every token forgeable.
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException(
                        "JWT_SECRET must be set and at least 32 bytes long");
            }
        }
    }

    public record Login(int maxFailures, Duration window) {
    }
}
