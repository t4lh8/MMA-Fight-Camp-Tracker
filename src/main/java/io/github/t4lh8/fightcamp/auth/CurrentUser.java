package io.github.t4lh8.fightcamp.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the caller from the token. The id always comes from the signed token, never from the request body. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public AppUser require(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        if (!(uid instanceof Number id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        // The account may have been deleted after the token was issued.
        return users.findById(id.longValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
    }

    public long id(Jwt jwt) {
        return require(jwt).id();
    }
}
