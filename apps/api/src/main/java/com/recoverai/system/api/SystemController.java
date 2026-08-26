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
    body.put("webhookReceived", webhooks.countByProcessingStatus("RECEIVED") + webhooks.countByProcessingStatus("PROCESSED"));
    body.put("webhookPending", webhooks.countByProcessingStatus("RECEIVED"));
    body.put("outboxPending", outbox.findPendingDue(java.time.Instant.now(), org.springframework.data.domain.PageRequest.of(0, 1)).size());
    body.put("deadLetterQueue", outbox.findByStatus("DEAD", org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements());
    body.put("deadLetterEvents", outbox.findByStatus("DEAD", org.springframework.data.domain.PageRequest.of(0, 20)).getContent().stream()
        .map(e -> java.util.Map.of(
            "id", e.getId().toString(),
            "eventType", e.getEventType(),
            "error", e.getLastError() == null ? "" : e.getLastError(),
            "attempts", e.getAttempts(),
            "createdAt", e.getCreatedAt().toString(),
            "lastFailure", e.getNextAttemptAt().toString()))
        .toList());
    body.put("aiService", Map.of(
        "enabled", props.ai().enabled(),
        "baseUrl", props.ai().baseUrl(),
        "mode", props.razorpay().mockMode() ? "deterministic-fallback" : "llm"));
    body.put("provider", Map.of(
        "name", "razorpay",
        "mode", props.razorpay().mockMode() ? "MOCK (SIMULATED)" : "TEST MODE",
        "mock", props.razorpay().mockMode()));
    body.put("eventDispatchMode", props.eventDispatchMode());
    body.put("temporalEnabled", props.temporal().enabled());
    body.put("demoMode", props.demoMode());
    body.put("metrics", Map.of(
        "duplicateCollectionPrevented", counterValue("duplicate_collection_prevented_total"),
        "recovered", counterValue("recovery_recovered_total"),
        "policyBlocks", counterValue("recovery_blocked_total"),
        "aiFallbacks", counterValue("ai_fallback_total"),
        "webhookInvalidSignatures", counterValue("webhook_invalid_signature_total"),
        "workflowFailures", counterValue("workflow_failure_total"),
        "providerErrors", counterValue("provider_error_total")));
    return body;
  }

  private double counterValue(String name) {
    var counter = registry.find(name).counter();
    return counter == null ? 0 : counter.count();
  }
}
