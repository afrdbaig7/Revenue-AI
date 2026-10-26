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
  private final com.recoverai.payment.application.PaymentService paymentService;
  private final com.recoverai.payment.infrastructure.PaymentRepository payments;
  private final org.springframework.transaction.PlatformTransactionManager transactionManager;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final com.recoverai.policy.application.PolicyEngine policyEngine;
  @org.springframework.beans.factory.annotation.Value("${recoverai.worker-enabled:true}")
  private boolean workerEnabled = true;

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
    if (amountMinor <= 0 || amountMinor > incident.getAmountMinor()) throw ApiException.badRequest("Promise amount must be positive and no greater than the incident amount");
    if (incident.getCustomerId() == null || !incident.getCustomerId().equals(customerId) || !incident.getCurrency().equals(currency)) throw ApiException.badRequest("Promise must match incident customer and currency");

    if (!intent.promisedAt().isAfter(Instant.now()) || (incident.getRecoveryWindowEndsAt() != null
        && intent.promisedAt().isAfter(incident.getRecoveryWindowEndsAt()))) throw ApiException.badRequest("Promise must be in the future within the recovery window");
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

    orchestrator.cancelPendingActions(orgId, incidentId, "Customer promised to pay later");
    incident.setStatus(IncidentStatus.AWAITING_PAYMENT);
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
      date = date.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
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

  /** Each promise has its own transaction; one failure cannot roll back another promise. */
  @Scheduled(fixedDelay = 60_000)
  public void processDuePromises() {
    if (!workerEnabled) return;
    var due = promises.findByStatusInAndPromisedAtBefore(
        List.of(PromiseToPay.Status.PROMISED, PromiseToPay.Status.SCHEDULED, PromiseToPay.Status.DUE), Instant.now(),
        org.springframework.data.domain.PageRequest.of(0, 100));
    for (var promise : due) {
      try { processPromise(promise.getOrgId(), promise.getId()); }
      catch (Exception error) { log.warn("PROMISE_PROCESSING_FAILED promise={} category={}", promise.getId(), error.getClass().getSimpleName()); }
    }
  }

  public void processPromise(UUID orgId, UUID promiseId) {
    var previousOrg = com.recoverai.common.tenant.TenantContext.orgId();
    com.recoverai.common.tenant.TenantContext.setOrgId(orgId);
    try {
      var snapshot = promises.findByOrgIdAndId(orgId, promiseId).orElseThrow(() -> ApiException.notFound("Promise not found"));
      if (snapshot.getPromisedAt().isAfter(Instant.now())) return;
      RevenueIncident initial = incidents.findByOrgIdAndId(orgId, snapshot.getIncidentId()).orElseThrow();
      if (initial.getPaymentId() != null && initial.isOpen()) {
        var payment = payments.findByOrgIdAndId(orgId, initial.getPaymentId()).orElseThrow();
        var result = paymentService.reconcileFromProvider(orgId, payment);
        if (!result.verified()) return;
        if (result.payment().getStatus().isCollectable()) orchestrator.onPaymentCollected(orgId, result.payment());
      }
      new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
        jdbc.query("SELECT id FROM promises_to_pay WHERE org_id = ? AND id = ? FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), orgId, promiseId);
        PromiseToPay promise = promises.findByOrgIdAndId(orgId, promiseId).orElseThrow();
        if (!List.of(PromiseToPay.Status.PROMISED, PromiseToPay.Status.SCHEDULED, PromiseToPay.Status.DUE).contains(promise.getStatus())) return;
        RevenueIncident incident = incidents.findByIdForUpdate(promise.getIncidentId()).filter(i -> i.getOrgId().equals(orgId)).orElseThrow();
        if (!incident.isOpen()) {
          promise.setStatus(incident.getStatus() == IncidentStatus.RECOVERED ? PromiseToPay.Status.FULFILLED : PromiseToPay.Status.CANCELLED);
          if (promise.getStatus() == PromiseToPay.Status.FULFILLED) promise.setFulfilledAt(Instant.now());
        } else if (incident.getRecoveryWindowEndsAt() != null && !incident.getRecoveryWindowEndsAt().isAfter(Instant.now())) {
          promise.setStatus(PromiseToPay.Status.MISSED);
        } else if (promise.getStatus() != PromiseToPay.Status.DUE) {
          PolicySet policy = policySets.findByOrgIdAndActiveTrue(orgId).orElseThrow();
          String strategy = switch (promise.getPreferredChannel()) { case "EMAIL" -> "EMAIL_NUDGE"; case "SMS" -> "SMS_NUDGE"; default -> "WHATSAPP_NUDGE"; };
          var allowed = policyEngine.evaluate(policy, null, strategy, incident.getAttemptsCount(), incident.getContactCount(), Instant.now(), incident.getAmountMinor());
          if (!allowed.allowed()) { promise.setStatus(PromiseToPay.Status.CANCELLED); }
          else {
            var message = communicationService.sendRecoveryMessage(orgId, incident.getId(), promise.getCustomerId(), Channel.DEMO_INBOX,
                "promise_reminder", Map.of("customerName", "there", "merchantName", "Merchant", "amount", "INR " + promise.getPromisedAmountMinor() / 100.0,
                "_operationKey", "promise:" + promiseId), incident.getContactCount(), policy.getMaxContactAttempts(), Duration.ofHours(policy.getContactCooldownHours()));
            if (message.getStatus() == com.recoverai.communication.domain.Communication.Status.SENT || message.getStatus() == com.recoverai.communication.domain.Communication.Status.SIMULATED) {
              promise.setStatus(PromiseToPay.Status.DUE);
              audit.record("PROMISE_REMINDER_SENT", "promise_to_pay", promiseId.toString(), incident.getId(), "SCHEDULED", "DUE", null, null, null);
            } else { promise.setStatus(PromiseToPay.Status.CANCELLED); }
          }
        }
        promises.save(promise);
      });
    } finally {
      if (previousOrg == null) com.recoverai.common.tenant.TenantContext.clear();
      else com.recoverai.common.tenant.TenantContext.setOrgId(previousOrg);
    }
  }
}
