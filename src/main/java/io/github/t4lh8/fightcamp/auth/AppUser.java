package io.github.t4lh8.fightcamp.auth;

import java.time.OffsetDateTime;

public record AppUser(long id, String username, String passwordHash, Role role,
                      WeightClass weightClass, OffsetDateTime createdAt) {
}
