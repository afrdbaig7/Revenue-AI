package com.recoverai.promise.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.audit.application.AuditService;
import com.recoverai.common.api.ApiException;
import com.recoverai.communication.application.CommunicationService;
import com.recoverai.communication.domain.Communication.Channel;
import com.recoverai.diagnosis.application.AiClient;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import com.recoverai.promise.domain.PromiseToPay;
import com.recoverai.promise.infrastructure.PromiseToPayRepository;
import com.recoverai.recovery.application.RecoveryOrchestrator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promise-to-pay: "Salary comes Monday", "Remind me on the 1st". Creates a durable
 * follow-up workflow; at the promised time the system reconciles payment state,
 * verifies opt-out/incident/policy, and executes a permitted reminder.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromiseService {

  private final PromiseToPayRepository promises;
  private final RevenueIncidentRepository incidents;
  private final PolicySetRepository policySets;
  private final CommunicationService communicationService;
  private final RecoveryOrchestrator orchestrator;
  private final com.recoverai.workflow.application.WorkflowLauncher workflowLauncher;
  private final AuditService audit;
  private final AiClient aiClient;
  private final ObjectMapper mapper;

  /** Structured promise extracted from free text (AI or deterministic fallback). */
  public record PromiseIntent(Instant promisedAt, String preferredTime, String channel, long amountMinor, double confidence) {}

  @Transactional
  public PromiseToPay create(UUID orgId, UUID incidentId, UUID customerId, long amountMinor, String currency, PromiseIntent intent) {
    RevenueIncident incident = incidents
        .findByOrgIdAndId(orgId, incidentId)
        .orElseThrow(() -> ApiException.notFound("Incident not found"));
    if (!incident.isOpen()) {
      throw ApiException.conflict("Incident is " + incident.getStatus());
    }

    PromiseToPay promise = new PromiseToPay();
    promise.setOrgId(orgId);
    promise.setIncidentId(incidentId);
    promise.setCustomerId(customerId);
    promise.setPromisedAmountMinor(amountMinor > 0 ? amountMinor : incident.getAmountMinor());
    promise.setCurrency(currency);
    promise.setPromisedAt(intent.promisedAt());
    promise.setPreferredTime(intent.preferredTime());
    promise.setPreferredChannel(intent.channel());
    promise.setStatus(PromiseToPay.Status.SCHEDULED);
    promise.setConfidence(BigDecimal.valueOf(intent.confidence()));
    promise.setSource("extraction");
    PromiseToPay saved = promises.save(promise);

    incident.setSelectedStrategy("PROMISE_TO_PAY");
    incident.setNextActionAt(intent.promisedAt());
    incidents.save(incident);

    // Durable follow-up timer: Temporal workflow when enabled; DB scan otherwise.
    workflowLauncher.launchPromise(saved);

    audit.record(
        "PROMISE_RECEIVED",
        "promise_to_pay",
        saved.getId().toString(),
        incidentId,
        null,
        "SCHEDULED",
        audit.json(Map.of(
            "incidentId", incidentId.toString(),
            "promisedAt", intent.promisedAt().toString(),
            "channel", intent.channel(),
            "confidence", intent.confidence())),
        null,
        null);
    return saved;
  }

  /** Extract a structured promise from natural language (Hinglish supported). */
  public PromiseIntent extract(String text, ZoneId zone) {
    ObjectNode req = mapper.createObjectNode();
    req.put("text", text);
    JsonNode ai = aiClient.call("/v1/promise/extract", req);
    if (ai != null && ai.hasNonNull("promisedDate")) {
      try {
        LocalDate date = LocalDate.parse(ai.path("promisedDate").asText());
        LocalTime time = ai.path("preferredTime").hasNonNull("hour")
            ? LocalTime.of(ai.path("preferredTime").path("hour").asInt(18), ai.path("preferredTime").path("minute").asInt(30))
            : LocalTime.of(18, 30);
        ZonedDateTime when = date.atTime(time).atZone(zone);
        String channel = ai.path("channel").asText("WHATSAPP");
        double confidence = ai.path("confidence").asDouble(0.7);
        return new PromiseIntent(when.toInstant(), ai.path("preferredTimeText").asText("evening"), channel, 0, confidence);
      } catch (Exception e) {
        log.warn("AI_PROMISE_PARSE_FAILED error={}", e.getMessage());
      }
    }
    return deterministicExtract(text, zone);
  }

  /** Deterministic fallback: tomorrow evening 18:30 (or "1st" / weekday keywords). */
  private PromiseIntent deterministicExtract(String text, ZoneId zone) {
    String lower = text.toLowerCase();
    LocalDate date = LocalDate.now(zone);
    if (lower.contains("monday")) {
      date = date.plusDays((8 - date.getDayOfWeek().getValue()) % 7 + 7);
    } else if (lower.contains("tomorrow")) {
      date = date.plusDays(1);
    } else if (lower.contains("1st") || lower.contains("first")) {
      date = date.withDayOfMonth(1).plusMonths(1);
    } else {
      date = date.plusDays(1);
    }
    String timeText = lower.contains("morning") ? "morning" : lower.contains("afternoon") ? "afternoon" : "evening";
    LocalTime time = switch (timeText) {
      case "morning" -> LocalTime.of(10, 0);
      case "afternoon" -> LocalTime.of(15, 0);
      default -> LocalTime.of(18, 30);
    };
    return new PromiseIntent(date.atTime(time).atZone(zone).toInstant(), timeText, "WHATSAPP", 0, 0.65);
  }

  /** Due-promise worker: reconcile, verify rules, send permitted reminder. */
  @Scheduled(fixedDelay = 60_000)
  @Transactional
  public void processDuePromises() {
    List<PromiseToPay> due = promises.findByStatusInAndPromisedAtBefore(
        List.of(PromiseToPay.Status.PROMISED, PromiseToPay.Status.SCHEDULED), Instant.now());
    for (PromiseToPay promise : due) {
      try {
        com.recoverai.common.tenant.TenantContext.setOrgId(promise.getOrgId());
        RevenueIncident incident = incidents
            .findByIdForUpdate(promise.getIncidentId())
            .orElse(null);
        if (incident == null || !incident.isOpen()) {
          promise.setStatus(PromiseToPay.Status.CANCELLED);
          promises.save(promise);
          continue;
        }
        // 1. Reconcile current payment state first.
        orchestrator.runPipeline(incident.getOrgId(), incident.getId());
        if (!incident.isOpen() || incident.getStatus() == IncidentStatus.RECOVERED) {
          promise.setStatus(PromiseToPay.Status.FULFILLED);
          promise.setFulfilledAt(Instant.now());
          promises.save(promise);
          continue;
        }
        PolicySet policy = policySets.findByOrgIdAndActiveTrue(incident.getOrgId()).orElse(null);
        if (policy == null || incident.getContactCount() >= policy.getMaxContactAttempts()) {
          promise.setStatus(PromiseToPay.Status.MISSED);
          promises.save(promise);
          continue;
        }
        promise.setStatus(PromiseToPay.Status.DUE);
        promises.save(promise);

        communicationService.sendRecoveryMessage(
            incident.getOrgId(),
            incident.getId(),
            promise.getCustomerId(),
            Channel.DEMO_INBOX,
            "promise_reminder",
            Map.of(
                "customerName", "there",
                "merchantName", "RecoverAI Demo",
                "amount", "₹" + (promise.getPromisedAmountMinor() / 100)),
            incident.getContactCount(),
            policy.getMaxContactAttempts(),
            Duration.ofHours(policy.getContactCooldownHours()));
        incident.setContactCount(incident.getContactCount() + 1);
        incidents.save(incident);

        audit.record("PROMISE_REMINDER_SENT", "promise_to_pay", promise.getId().toString(), promise.getIncidentId(), "SCHEDULED", "DUE", null, null, null);
      } catch (Exception e) {
        log.warn("PROMISE_PROCESSING_FAILED promise={} error={}", promise.getId(), e.getMessage());
      } finally {
        com.recoverai.common.tenant.TenantContext.clear();
      }
    }
  }
}
