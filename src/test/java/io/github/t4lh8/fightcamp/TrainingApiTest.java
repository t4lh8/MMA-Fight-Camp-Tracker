package io.github.t4lh8.fightcamp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TrainingApiTest extends IntegrationTest {

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Oslo"));

    private Map<String, Object> session(Object rpe) {
        Map<String, Object> s = new HashMap<>();
        s.put("date", TODAY.toString());
        s.put("discipline", "SPARRING");
        s.put("durationMin", 60);
        s.put("rpe", rpe);
        s.put("rounds", 6);
        return s;
    }

    @Test
    void validSessionIsStored() throws Exception {
        String token = newAthlete();
        mvc.perform(as(token, jsonPost("/api/sessions", session(8)))).andExpect(status().isCreated());
        mvc.perform(as(token, get("/api/sessions")))
                .andExpect(jsonPath("$[0].discipline").value("SPARRING"))
                .andExpect(jsonPath("$[0].load").value(480));
    }

    @Test
    void outOfRangeValuesAreRejected() throws Exception {
        String token = newAthlete();
        mvc.perform(as(token, jsonPost("/api/sessions", session(11)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.rpe").exists());

        Map<String, Object> future = session(5);
        future.put("date", TODAY.plusDays(2).toString());
        mvc.perform(as(token, jsonPost("/api/sessions", future))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.date").exists());

        Map<String, Object> longNotes = session(5);
        longNotes.put("notes", "x".repeat(501));
        mvc.perform(as(token, jsonPost("/api/sessions", longNotes))).andExpect(status().isBadRequest());

        Map<String, Object> badEnum = session(5);
        badEnum.put("discipline", "BOXING'; DROP TABLE training_session;--");
        mvc.perform(as(token, jsonPost("/api/sessions", badEnum))).andExpect(status().isBadRequest());
    }

    @Test
    void sqlInPlainTextFieldsIsStoredAsText() throws Exception {
        String token = newAthlete();
        Map<String, Object> s = session(5);
        s.put("notes", "'); DELETE FROM app_user; --");
        mvc.perform(as(token, jsonPost("/api/sessions", s))).andExpect(status().isCreated());
        mvc.perform(as(token, get("/api/sessions")))
                .andExpect(jsonPath("$[0].notes").value("'); DELETE FROM app_user; --"));
        mvc.perform(as(token, get("/api/me"))).andExpect(status().isOk());
    }

    @Test
    void drawCannotEndByKnockout() throws Exception {
        String token = newAthlete();
        mvc.perform(as(token, jsonPost("/api/fights", Map.of("date", TODAY.minusDays(30).toString(),
                        "opponent", "Kari Nordmann", "result", "DRAW", "method", "KO_TKO", "endRound", 2))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weighInOnTheSameDayReplacesTheOldValue() throws Exception {
        String token = newAthlete();
        mvc.perform(as(token, jsonPost("/api/weigh-ins", Map.of("date", TODAY.toString(), "kg", 65.4))))
                .andExpect(status().isCreated());
        mvc.perform(as(token, jsonPost("/api/weigh-ins", Map.of("date", TODAY.toString(), "kg", 64.9))))
                .andExpect(status().isCreated());
        mvc.perform(as(token, get("/api/weigh-ins")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kg").value(64.9));
    }

    @Test
    void malformedJsonGivesAShortError() throws Exception {
        String token = newAthlete();
        mvc.perform(as(token, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sessions")
                        .contentType("application/json").content("{not json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
