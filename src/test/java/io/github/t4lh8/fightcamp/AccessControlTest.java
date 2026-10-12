package io.github.t4lh8.fightcamp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Broken object level authorization (OWASP API1) and role checks. */
class AccessControlTest extends IntegrationTest {

    private long addSession(String token) throws Exception {
        var res = read(mvc.perform(as(token, jsonPost("/api/sessions", Map.of(
                "date", LocalDate.now().minusDays(1).toString(), "discipline", "BJJ",
                "durationMin", 60, "rpe", 6, "notes", "private notes")))).andExpect(status().isCreated()));
        return res.get("id").asLong();
    }

    @Test
    void athleteCannotDeleteAnotherAthletesSession() throws Exception {
        String alice = newAthlete();
        String bob = newAthlete();
        long aliceSession = addSession(alice);

        mvc.perform(as(bob, delete("/api/sessions/" + aliceSession))).andExpect(status().isNotFound());
        // Still there for its owner.
        mvc.perform(as(alice, get("/api/sessions")))
                .andExpect(jsonPath("$[0].id").value(aliceSession));
        mvc.perform(as(alice, delete("/api/sessions/" + aliceSession))).andExpect(status().isNoContent());
    }

    @Test
    void athleteOnlySeesOwnData() throws Exception {
        String alice = newAthlete();
        String bob = newAthlete();
        addSession(alice);

        mvc.perform(as(bob, get("/api/sessions"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ownerIdInTheBodyIsRejected() throws Exception {
        String bob = newAthlete();
        mvc.perform(as(bob, jsonPost("/api/sessions", Map.of(
                        "date", LocalDate.now().toString(), "discipline", "BJJ", "durationMin", 60, "rpe", 6,
                        "ownerId", 1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAndForeignIdsLookTheSame() throws Exception {
        String alice = newAthlete();
        String bob = newAthlete();
        long aliceSession = addSession(alice);

        String foreign = mvc.perform(as(bob, delete("/api/sessions/" + aliceSession)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(as(bob, delete("/api/sessions/999999999")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(foreign).isEqualTo(missing);
    }

    @Test
    void coachNeedsTheAthletesPermission() throws Exception {
        String athleteName = uniqueName("frida");
        register(athleteName, "ATHLETE", "FLYWEIGHT");
        String athlete = login(athleteName);
        String coachName = uniqueName("coach");
        String coach = newCoach(coachName);

        String url = "/api/coach/athletes/" + athleteName + "/dashboard";
        mvc.perform(as(coach, get(url))).andExpect(status().isNotFound());

        mvc.perform(as(athlete, jsonPost("/api/me/coaches", Map.of("coach", coachName)))).andExpect(status().isOk());
        mvc.perform(as(coach, get(url))).andExpect(status().isOk()).andExpect(jsonPath("$.athlete").value(athleteName));
        mvc.perform(as(coach, get("/api/coach/athletes"))).andExpect(jsonPath("$[0]").value(athleteName));

        mvc.perform(as(athlete, delete("/api/me/coaches/" + coachName))).andExpect(status().isNoContent());
        mvc.perform(as(coach, get(url))).andExpect(status().isNotFound());
    }

    @Test
    void deniedCoachAccessIsAudited() throws Exception {
        String coachName = uniqueName("curious");
        String coach = newCoach(coachName);
        mvc.perform(as(coach, get("/api/coach/athletes/someone_else/dashboard"))).andExpect(status().isNotFound());
        mvc.perform(as(coach, get("/api/me/audit"))).andExpect(jsonPath("$[0].action").value("ACCESS_DENIED"));
    }

    @Test
    void canOnlyShareWithCoaches() throws Exception {
        String athlete = newAthlete();
        String otherAthlete = uniqueName("notacoach");
        register(otherAthlete, "ATHLETE", null);
        mvc.perform(as(athlete, jsonPost("/api/me/coaches", Map.of("coach", otherAthlete)))).andExpect(status().isNotFound());
    }

    @Test
    void rolesAreEnforced() throws Exception {
        String athlete = newAthlete();
        String coach = newCoach(uniqueName("roles"));

        mvc.perform(as(athlete, get("/api/coach/athletes"))).andExpect(status().isForbidden());
        mvc.perform(as(coach, get("/api/dashboard"))).andExpect(status().isForbidden());
        mvc.perform(as(coach, get("/api/sessions"))).andExpect(status().isForbidden());
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        String athlete = newAthlete();
        mvc.perform(as(athlete, get("/actuator/env"))).andExpect(status().isForbidden());
    }
}
