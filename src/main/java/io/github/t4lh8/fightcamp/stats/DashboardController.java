package io.github.t4lh8.fightcamp.stats;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.t4lh8.fightcamp.auth.CurrentUser;

@RestController
public class DashboardController {

    private final DashboardService dashboards;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboards, CurrentUser currentUser) {
        this.dashboards = dashboards;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/dashboard")
    public DashboardService.Dashboard dashboard(@AuthenticationPrincipal Jwt jwt) {
        return dashboards.build(currentUser.require(jwt));
    }
}
