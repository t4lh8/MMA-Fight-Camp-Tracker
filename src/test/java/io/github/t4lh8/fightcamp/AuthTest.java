package io.github.t4lh8.fightcamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.github.t4lh8.fightcamp.auth.AppUser;
import io.github.t4lh8.fightcamp.auth.Role;
import io.github.t4lh8.fightcamp.auth.TokenService;
import io.github.t4lh8.fightcamp.auth.UserRepository;
import io.github.t4lh8.fightcamp.common.AppProperties;

class AuthTest extends IntegrationTest {

    @Autowired
    JwtEncoder encoder;

    @Autowired
    AppProperties props;

    @Autowired
    UserRepository users;

    @Test
    void registerAndLoginReturnsAToken() throws Exception {
        String name = uniqueName("ingrid");
        register(name, "ATHLETE", "BANTAMWEIGHT");
        String token = login(name);

        mvc.perform(as(token, get("/api/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.role").value("ATHLETE"))
                .andExpect(jsonPath("$.weightClass").value("BANTAMWEIGHT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        String name = uniqueName("hash");
        register(name, "ATHLETE", null);
        String hash = users.findByUsername(name).orElseThrow().passwordHash();
        assertThat(hash).startsWith("$2a$12$").doesNotContain(PASSWORD);
    }

    @Test
    void weakPasswordsAreRejected() throws Exception {
        for (String weak : new String[] {"short1!", "password1234", "aaaaaaaaaaaaaaa"}) {
            mvc.perform(jsonPost("/api/auth/register",
                            Map.of("username", uniqueName("weak"), "password", weak, "role", "ATHLETE")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.password").exists());
        }
    }

    @Test
    void passwordContainingUsernameIsRejected() throws Exception {
        String name = uniqueName("magnus");
        mvc.perform(jsonPost("/api/auth/register",
                        Map.of("username", name, "password", name + "-Strong-99", "role", "ATHLETE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateUsernameGives409() throws Exception {
        String name = uniqueName("dup");
        register(name, "ATHLETE", null);
        mvc.perform(jsonPost("/api/auth/register", Map.of("username", name, "password", PASSWORD, "role", "ATHLETE")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidUsernameIsRejected() throws Exception {
        mvc.perform(jsonPost("/api/auth/register",
                        Map.of("username", "<script>", "password", PASSWORD, "role", "ATHLETE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").exists());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameAnswer() throws Exception {
        String name = uniqueName("enum");
        register(name, "ATHLETE", null);

        String wrongPassword = mvc.perform(jsonPost("/api/auth/login", Map.of("username", name, "password", "Wrong-Password-123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(jsonPost("/api/auth/login", Map.of("username", uniqueName("ghost"), "password", "Wrong-Password-123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownUser);
    }

    @Test
    void accountLocksAfterFiveFailedLogins() throws Exception {
        String name = uniqueName("brute");
        register(name, "ATHLETE", null);
        for (int i = 0; i < 5; i++) {
            mvc.perform(jsonPost("/api/auth/login", Map.of("username", name, "password", "Guess-number-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused while the account is locked.
        mvc.perform(jsonPost("/api/auth/login", Map.of("username", name, "password", PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        String name = uniqueName("reset");
        register(name, "ATHLETE", null);
        for (int i = 0; i < 4; i++) {
            mvc.perform(jsonPost("/api/auth/login", Map.of("username", name, "password", "Guess-number-" + i)));
        }
        login(name);
        mvc.perform(jsonPost("/api/auth/login", Map.of("username", name, "password", "Guess-again-1")))
                .andExpect(status().isUnauthorized());
        login(name);
    }

    @Test
    void loginsShowUpInTheUsersOwnAuditLog() throws Exception {
        String name = uniqueName("audit");
        register(name, "ATHLETE", null);
        mvc.perform(jsonPost("/api/auth/login", Map.of("username", name, "password", "Not-my-password-1")));
        String token = login(name);

        mvc.perform(as(token, get("/api/me/audit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("LOGIN_OK"))
                .andExpect(jsonPath("$[1].action").value("LOGIN_FAIL"))
                .andExpect(jsonPath("$[2].action").value("REGISTER"));
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String name = uniqueName("expired");
        register(name, "ATHLETE", null);
        AppUser user = users.findByUsername(name).orElseThrow();
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = new TokenService(encoder, props, twoHoursAgo).issue(user).token();

        mvc.perform(as(token, get("/api/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String name = uniqueName("forged");
        register(name, "ATHLETE", null);
        AppUser user = users.findByUsername(name).orElseThrow();
        var otherKey = new SecretKeySpec("attacker-key-that-is-also-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String forged = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)), props, Clock.systemUTC())
                .issue(user).token();

        mvc.perform(as(forged, get("/api/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() throws Exception {
        String name = uniqueName("none");
        register(name, "ATHLETE", null);
        long uid = users.findByUsername(name).orElseThrow().id();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        long exp = Instant.now().plusSeconds(600).getEpochSecond();
        String payload = b64.encodeToString(("{\"iss\":\"fightcamp\",\"sub\":\"" + name + "\",\"uid\":" + uid
                + ",\"roles\":[\"COACH\"],\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));

        mvc.perform(as(header + "." + payload + ".", get("/api/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountsTokenStopsWorking() throws Exception {
        String token = newAthlete();
        mvc.perform(as(token, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/me")))
                .andExpect(status().isNoContent());
        mvc.perform(as(token, get("/api/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersAreSet() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void roleComesFromTheServerNotTheClient() throws Exception {
        String name = uniqueName("sneaky");
        mvc.perform(jsonPost("/api/auth/register",
                        Map.of("username", name, "password", PASSWORD, "role", "ADMIN")))
                .andExpect(status().isBadRequest());
        assertThat(Role.values()).extracting(Enum::name).containsExactly("ATHLETE", "COACH");
    }
}
