package io.github.t4lh8.fightcamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Checks the SQL aggregations against numbers worked out by hand. */
class DashboardTest extends IntegrationTest {

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Oslo"));

    private void session(String token, int daysAgo, String discipline, int minutes, int rpe) throws Exception {
        mvc.perform(as(token, jsonPost("/api/sessions", Map.of("date", TODAY.minusDays(daysAgo).toString(),
                "discipline", discipline, "durationMin", minutes, "rpe", rpe)))).andExpect(status().isCreated());
    }

    private void fight(String token, int monthsAgo, String result, String method) throws Exception {
        mvc.perform(as(token, jsonPost("/api/fights", Map.of("date", TODAY.minusMonths(monthsAgo).toString(),
                "opponent", "Opponent " + monthsAgo, "result", result, "method", method, "endRound", 3))))
                .andExpect(status().isCreated());
    }

    @Test
    void emptyDashboardHasNoBaseline() throws Exception {
        String token = newAthlete();
        JsonNode d = read(mvc.perform(as(token, get("/api/dashboard"))).andExpect(status().isOk()));
        assertThat(d.at("/workload/status").asText()).isEqualTo("NO_BASELINE");
        assertThat(d.at("/record/wins").asInt()).isZero();
        assertThat(d.at("/record/streak").isMissingNode()).isTrue();
        assertThat(d.get("weeks")).hasSize(8);
        assertThat(d.get("weightCut")).isNull();
    }

    @Test
    void workloadRecordAndWeightCutAreCalculated() throws Exception {
        String token = newAthlete(); // bantamweight, limit 61.2 kg

        session(token, 0, "SPARRING", 60, 8);   // 480
        session(token, 3, "BJJ", 90, 6);        // 540, acute = 1020
        session(token, 20, "STRIKING", 60, 5);  // 300, 28-day load = 1320
        session(token, 40, "STRENGTH", 60, 5);  // outside the 28-day window

        fight(token, 12, "WIN", "KO_TKO");
        fight(token, 9, "WIN", "SUBMISSION");
        fight(token, 6, "WIN", "DECISION");
        fight(token, 3, "LOSS", "DECISION");

        mvc.perform(as(token, jsonPost("/api/weigh-ins", Map.of("date", TODAY.minusDays(1).toString(), "kg", 67.0))));
        mvc.perform(as(token, jsonPost("/api/weigh-ins", Map.of("date", TODAY.toString(), "kg", 66.0))));

        JsonNode d = read(mvc.perform(as(token, get("/api/dashboard"))).andExpect(status().isOk()));

        // chronic = 1320 / 4 = 330 per week, ratio = 1020 / 330 = 3.09
        assertThat(d.at("/workload/acute").asLong()).isEqualTo(1020);
        assertThat(d.at("/workload/chronicWeekly").asLong()).isEqualTo(330);
        assertThat(d.at("/workload/ratio").decimalValue()).isEqualByComparingTo("3.09");
        assertThat(d.at("/workload/status").asText()).isEqualTo("SPIKE");

        assertThat(d.at("/record/wins").asInt()).isEqualTo(3);
        assertThat(d.at("/record/losses").asInt()).isEqualTo(1);
        assertThat(d.at("/record/koWins").asInt()).isEqualTo(1);
        assertThat(d.at("/record/subWins").asInt()).isEqualTo(1);
        assertThat(d.at("/record/finishRate").asInt()).isEqualTo(67);
        assertThat(d.at("/record/streak").asText()).isEqualTo("L1");

        // 66.0 - 61.2 = 4.8 kg, 4.8 / 66.0 = 7.3 %
        assertThat(d.at("/weightCut/latestKg").decimalValue()).isEqualByComparingTo("66.0");
        assertThat(d.at("/weightCut/kgToCut").decimalValue()).isEqualByComparingTo("4.8");
        assertThat(d.at("/weightCut/percentOfBodyweight").decimalValue()).isEqualByComparingTo("7.3");
        assertThat(d.at("/weightCut/status").asText()).isEqualTo("HEAVY");

        long weeklyTotal = 0;
        for (JsonNode w : d.get("weeks")) {
            weeklyTotal += w.get("load").asLong();
        }
        assertThat(d.get("weeks")).hasSize(8);
        assertThat(weeklyTotal).isEqualTo(1320 + 300); // the 40-day-old session is inside the 8-week chart

        assertThat(d.at("/disciplines/0/discipline").asText()).isEqualTo("BJJ");
        assertThat(d.get("disciplines")).hasSize(3);
    }
}
