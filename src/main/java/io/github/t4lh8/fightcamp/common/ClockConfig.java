package io.github.t4lh8.fightcamp.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Oslo"));
    }

    /** @PastOrPresent should mean "today in Oslo", not today in the server's time zone. */
    @Bean
    ValidationConfigurationCustomizer validationClock(Clock clock) {
        return config -> config.clockProvider(() -> clock);
    }
}
