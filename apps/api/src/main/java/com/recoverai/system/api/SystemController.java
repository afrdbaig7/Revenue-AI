package com.recoverai.system.api;

import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.outbox.infrastructure.OutboxEventRepository;
import com.recoverai.webhook.infrastructure.WebhookInboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** System health: webhook processing, outbox/DLQ, AI status, provider status. */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

  private final WebhookInboxRepository webhooks;
  private final OutboxEventRepository outbox;
  private final RecoverAiProperties props;
  private final MeterRegistry registry;

  @GetMapping("/health")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','ANALYST')")
  public Map<String, Object> health(Authentication authentication) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    Map<String, Object> body = new HashMap<>();
    body.put("webhookReceived", webhooks.countByOrgId(orgId));
    body.put("webhookPending", webhooks.countByOrgIdAndProcessingStatus(orgId, "RECEIVED"));
    body.put("outboxPending", outbox.countByOrgIdAndStatus(orgId, "PENDING") + outbox.countByOrgIdAndStatus(orgId, "PROCESSING"));
    body.put("deadLetterQueue", outbox.findByOrgIdAndStatus(orgId, "DEAD", org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements());
    body.put("deadLetterEvents", outbox.findByOrgIdAndStatus(orgId, "DEAD", org.springframework.data.domain.PageRequest.of(0, 20)).getContent().stream()
        .map(e -> java.util.Map.of(
            "id", e.getId().toString(),
            "eventType", e.getEventType(),
            "error", "Delivery failed; inspect authorized operational logs",
            "attempts", e.getAttempts(),
            "createdAt", e.getCreatedAt().toString(),
            "lastFailure", e.getNextAttemptAt().toString()))
        .toList());
    body.put("aiService", Map.of(
        "enabled", props.ai().enabled(),
        "mode", props.ai().enabled() ? "advisory-with-fallback" : "disabled"));
    body.put("provider", Map.of(
        "name", "razorpay",
        "mode", props.razorpay().mockMode() ? "MOCK (SIMULATED)" : "TEST MODE",
        "mock", props.razorpay().mockMode()));
    body.put("eventDispatchMode", props.eventDispatchMode());
    body.put("temporalEnabled", props.temporal().enabled());
    body.put("demoMode", props.demoMode());
    body.put("metrics", Map.of()); // Process-wide telemetry is available only through protected monitoring.
    return body;
  }

  private double counterValue(String name) {
    var counter = registry.find(name).counter();
    return counter == null ? 0 : counter.count();
  }
}
