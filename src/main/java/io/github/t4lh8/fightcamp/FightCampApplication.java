package io.github.t4lh8.fightcamp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FightCampApplication {

    public static void main(String[] args) {
        SpringApplication.run(FightCampApplication.class, args);
    }
}
