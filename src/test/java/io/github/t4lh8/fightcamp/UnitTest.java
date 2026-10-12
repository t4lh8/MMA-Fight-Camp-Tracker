package io.github.t4lh8.fightcamp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.t4lh8.fightcamp.auth.LoginThrottle;
import io.github.t4lh8.fightcamp.auth.PasswordPolicy;
import io.github.t4lh8.fightcamp.auth.WeightClass;
import io.github.t4lh8.fightcamp.common.AppProperties;
import io.github.t4lh8.fightcamp.stats.DashboardServiceAccess;
import io.github.t4lh8.fightcamp.stats.WeightCut;
import io.github.t4lh8.fightcamp.stats.Workload;

/** Plain unit tests, no Spring and no database. */
class UnitTest {

    @ParameterizedTest
    @CsvSource({
            "700, 4000, 0.70, UNDERLOADED",
            "1000, 4000, 1.00, SWEET_SPOT",
            "1300, 4000, 1.30, SWEET_SPOT",
            "1400, 4000, 1.40, CAUTION",
            "1600, 4000, 1.60, SPIKE"})
    void workloadStatus(long acute, long load28, String ratio, Workload.Status expected) {
        Workload w = Workload.of(acute, load28);
        assertThat(w.chronicWeekly()).isEqualTo(1000);
        assertThat(w.ratio()).isEqualByComparingTo(ratio);
        assertThat(w.status()).isEqualTo(expected);
    }

    @Test
    void workloadWithoutHistoryHasNoRatio() {
        Workload w = Workload.of(500, 0);
        assertThat(w.ratio()).isNull();
        assertThat(w.status()).isEqualTo(Workload.Status.NO_BASELINE);
    }

    @ParameterizedTest
    @CsvSource({
            "LIGHTWEIGHT, 70.0, 0.0, ON_WEIGHT",
            "LIGHTWEIGHT, 72.0, 1.7, MANAGEABLE",
            "LIGHTWEIGHT, 75.5, 5.2, HEAVY",
            "LIGHTWEIGHT, 78.0, 7.7, EXTREME"})
    void weightCut(WeightClass wc, BigDecimal kg, BigDecimal toCut, WeightCut.Status expected) {
        WeightCut cut = WeightCut.of(wc, kg, LocalDate.of(2026, 10, 1));
        assertThat(cut.kgToCut()).isEqualByComparingTo(toCut);
        assertThat(cut.status()).isEqualTo(expected);
    }

    @Test
    void passwordPolicy() {
        PasswordPolicy policy = new PasswordPolicy();
        assertThat(policy.problems("ingrid", "Rear-Naked-Choke-77")).isEmpty();
        assertThat(policy.problems("ingrid", "short")).containsExactly("must be at least 12 characters");
        assertThat(policy.problems("ingrid", "PASSWORD1234")).contains("is a commonly used password");
        assertThat(policy.problems("ingrid", "ingrid-Strong-2026")).contains("must not contain the username");
        assertThat(policy.problems("ingrid", "abababababab")).contains("uses too few different characters");
        assertThat(policy.problems("ingrid", "x".repeat(129))).contains("must be at most 128 characters");
    }

    @Test
    void loginThrottleLocksAndUnlocks() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
        var props = new AppProperties(new AppProperties.Jwt(IntegrationTest.SECRET, Duration.ofMinutes(30)),
                new AppProperties.Login(3, Duration.ofMinutes(15)));
        LoginThrottle throttle = new LoginThrottle(props, clock);

        throttle.recordFailure("Ingrid");
        throttle.recordFailure("ingrid");
        assertThat(throttle.lockedFor("INGRID")).isEmpty();
        throttle.recordFailure("ingrid");
        assertThat(throttle.lockedFor("ingrid")).contains(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(10));
        assertThat(throttle.lockedFor("ingrid")).contains(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5));
        assertThat(throttle.lockedFor("ingrid")).isEmpty();
        assertThat(throttle.lockedFor("someone-else")).isEmpty();
    }

    @Test
    void shortJwtSecretStopsTheApp() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AppProperties.Jwt("too-short", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AppProperties.Jwt(null, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void streak() {
        assertThat(DashboardServiceAccess.streak(List.of("WIN", "WIN", "LOSS", "WIN"))).isEqualTo("W2");
        assertThat(DashboardServiceAccess.streak(List.of("LOSS"))).isEqualTo("L1");
        assertThat(DashboardServiceAccess.streak(List.of())).isNull();
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
