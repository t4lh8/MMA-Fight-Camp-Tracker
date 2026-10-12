package io.github.t4lh8.fightcamp.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Length over complexity rules, in line with NIST SP 800-63B: at least 12 characters,
 * not a known common password and not built from the username.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;

    private final Set<String> common;

    public PasswordPolicy() {
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            common = reader.lines().map(String::strip).filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> problems(String username, String password) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("must be at least " + MIN_LENGTH + " characters");
            return problems;
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("must be at most " + MAX_LENGTH + " characters");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (common.contains(lower)) {
            problems.add("is a commonly used password");
        }
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            problems.add("must not contain the username");
        }
        if (lower.chars().distinct().count() < 5) {
            problems.add("uses too few different characters");
        }
        return problems;
    }
}
