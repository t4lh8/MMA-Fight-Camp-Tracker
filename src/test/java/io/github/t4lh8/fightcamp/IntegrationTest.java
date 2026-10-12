package io.github.t4lh8.fightcamp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Runs against a real PostgreSQL in Docker, so the Flyway schema, CHECK constraints and SQL are all exercised. */
@SpringBootTest(properties = "fightcamp.jwt.secret=" + IntegrationTest.SECRET)
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    public static final String SECRET = "test-only-secret-with-at-least-32-bytes!";
    public static final String PASSWORD = "Rear-Naked-Choke-77";

    // One container for the whole test run, shared by the cached Spring context.
    @ServiceConnection
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

    static {
        DB.start();
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected static String uniqueName(String prefix) {
        return prefix + "_" + SEQ.incrementAndGet() + "_" + Long.toString(System.nanoTime() % 100000, 36);
    }

    protected String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected MockHttpServletRequestBuilder jsonPost(String url, Object value) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body(value));
    }

    protected static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected void register(String username, String role, String weightClass) throws Exception {
        var req = new java.util.HashMap<String, Object>(Map.of("username", username, "password", PASSWORD, "role", role));
        if (weightClass != null) {
            req.put("weightClass", weightClass);
        }
        mvc.perform(jsonPost("/api/auth/register", req)).andExpect(status().isCreated());
    }

    protected String login(String username) throws Exception {
        String res = mvc.perform(jsonPost("/api/auth/login", Map.of("username", username, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    protected String newAthlete() throws Exception {
        String name = uniqueName("athlete");
        register(name, "ATHLETE", "BANTAMWEIGHT");
        return login(name);
    }

    protected String newCoach(String name) throws Exception {
        register(name, "COACH", null);
        return login(name);
    }

    protected JsonNode read(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
