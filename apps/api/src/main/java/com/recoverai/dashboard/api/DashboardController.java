package com.recoverai.dashboard.api;

import com.recoverai.analytics.application.DashboardService;
import com.recoverai.common.tenant.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Overview dashboard endpoints. All tenant-scoped from the authenticated principal. */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboard;

  @GetMapping("/summary")
  public DashboardService.Summary summary(Authentication authentication) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    return dashboard.summary(user.orgId());
  }

  @GetMapping("/trend")
  public JsonNode trend(Authentication authentication, @RequestParam(defaultValue = "7") int days) {
    return dashboard.recoveryTrend(((CurrentUser) authentication.getPrincipal()).orgId(), Math.min(days, 90));
  }

  @GetMapping("/strategies")
  public JsonNode strategies(Authentication authentication) {
    return dashboard.byStrategy(((CurrentUser) authentication.getPrincipal()).orgId());
  }

  @GetMapping("/failures")
  public JsonNode failures(Authentication authentication) {
    return dashboard.byFailureReason(((CurrentUser) authentication.getPrincipal()).orgId());
  }
}
