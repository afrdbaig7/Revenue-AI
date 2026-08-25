package com.recoverai.recovery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.approval.domain.Approval;
import com.recoverai.approval.infrastructure.ApprovalRepository;
import com.recoverai.audit.application.AuditService;
import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.communication.application.CommunicationService;
import com.recoverai.communication.domain.Communication.Channel;
import com.recoverai.customer.domain.Customer;
import com.recoverai.customer.infrastructure.CustomerRepository;
import com.recoverai.diagnosis.application.AiClient;
import com.recoverai.diagnosis.application.AiRanking;
import com.recoverai.diagnosis.application.DiagnosisService;
import com.recoverai.diagnosis.domain.IncidentDiagnosis;
import com.recoverai.incident.application.IncidentService;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.integration.domain.PaymentProvider;
import com.recoverai.integration.domain.PaymentProvider.PaymentLink;
import com.recoverai.integration.domain.PaymentProvider.ProviderException;
import com.recoverai.integration.domain.PaymentProvider.ProviderOrder;
import com.recoverai.payment.application.PaymentService;
import com.recoverai.payment.domain.FailureCategory;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentStatus;
import com.recoverai.payment.infrastructure.PaymentRepository;
import com.recoverai.policy.application.PolicyEngine;
import com.recoverai.policy.application.PolicyEngine.Evaluation;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.domain.RecoveryAction.Status;
import com.recoverai.recovery.domain.RecoveryAttempt;
import com.recoverai.recovery.domain.RecoveryDecision;
import com.recoverai.recovery.domain.RecoveryDecision.CandidateView;
import com.recoverai.recovery.infrastructure.RecoveryActionRepository;
import com.recoverai.recovery.infrastructure.RecoveryAttemptRepository;
import com.recoverai.recovery.infrastructure.RecoveryDecisionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recovery pipeline orchestrator — the deterministic heart of RecoverAI.
 *
 * <pre>
 * DETECTED → RECONCILING → DIAGNOSING → STRATEGY_SELECTED → POLICY_EVALUATING
 *   → (AWAITING_APPROVAL) → SCHEDULED → EXECUTING → RECOVERED | RETRYABLE_FAILURE | FAILED
 * </pre>
 *
 * AI is consulted only inside diagnose/rank steps and its output is validated; every
 * authorization, limit, and execution decision below is deterministic and audited.
 */
@Service
@Slf4j
public class RecoveryOrchestrator {

  private final RevenueIncidentRepository incidents;
  private final PaymentRepository payments;
  private final PolicySetRepository policySets;
  private final RecoveryActionRepository actions;
  private final RecoveryAttemptRepository attempts;
  private final RecoveryDecisionRepository decisions;
  private final ApprovalRepository approvals;
  private final CustomerRepository customers;

  private final IncidentService incidentService;
  private final PaymentService paymentService;
  private final DiagnosisService diagnosisService;
  private final AiClient aiClient;
  private final CandidateGenerator candidateGenerator;
  private final DecisionEngine decisionEngine;
  private final PolicyEngine policyEngine;
  private final CommunicationService communicationService;
  private final PaymentProvider provider;
  private final AuditService audit;
  private final com.recoverai.workflow.application.WorkflowLauncher workflowLauncher;
  private final ObjectMapper mapper;
  private final Counter recoveredCounter;
  private final Counter blockedCounter;
  private final Counter lateAuthCounter;

  public RecoveryOrchestrator(
      RevenueIncidentRepository incidents,
      PaymentRepository payments,
      PolicySetRepository policySets,
      RecoveryActionRepository actions,
      RecoveryAttemptRepository attempts,
      RecoveryDecisionRepository decisions,
      ApprovalRepository approvals,
      CustomerRepository customers,
      IncidentService incidentService,
      PaymentService paymentService,
      DiagnosisService diagnosisService,
      AiClient aiClient,
      CandidateGenerator candidateGenerator,
      DecisionEngine decisionEngine,
      PolicyEngine policyEngine,
      CommunicationService communicationService,
      PaymentProvider provider,
      AuditService audit,
      com.recoverai.workflow.application.WorkflowLauncher workflowLauncher,
      ObjectMapper mapper,
      MeterRegistry registry) {
    this.incidents = incidents;
    this.payments = payments;
    this.policySets = policySets;
    this.actions = actions;
    this.attempts = attempts;
    this.decisions = decisions;
    this.approvals = approvals;
    this.customers = customers;
    this.incidentService = incidentService;
    this.paymentService = paymentService;
    this.diagnosisService = diagnosisService;
    this.aiClient = aiClient;
    this.candidateGenerator = candidateGenerator;
    this.decisionEngine = decisionEngine;
    this.policyEngine = policyEngine;
    this.communicationService = communicationService;
    this.provider = provider;
    this.audit = audit;
    this.workflowLauncher = workflowLauncher;
    this.mapper = mapper;
    this.recoveredCounter = Counter.builder("recovery_recovered_total").register(registry);
    this.blockedCounter = Counter.builder("recovery_blocked_total").register(registry);
    this.lateAuthCounter =
        Counter.builder("duplicate_collection_prevented_total").description("Late authorizations prevented double collection").register(registry);
  }

  /**
   * Advance an incident as far as the pipeline permits. Callers: event handlers (worker
   * context), the scheduler, and the reconciliation job. Never called from the webhook
   * acknowledgement path.
   */
  @Transactional
  public void runPipeline(UUID orgId, UUID incidentId) {
    RevenueIncident incident = incidents
        .findByIdForUpdate(incidentId)
        .orElseThrow(() -> com.recoverai.common.api.ApiException.notFound("Incident not found"));
    if (!incident.getOrgId().equals(orgId) || incident.getStatus().isTerminal()) {
      return;
    }

    Payment payment = incident.getPaymentId() == null
        ? null
        : payments.findById(incident.getPaymentId()).orElse(null);

    switch (incident.getStatus()) {
      case DETECTED -> {
        audit.record("PAYMENT_RECONCILING", "revenue_incident", incidentId.toString(), "DETECTED", "RECONCILING", null, null, null);
        incident.setStatus(IncidentStatus.RECONCILING);
        incidents.save(incident);
        runPipeline(orgId, incidentId); // re-enter under the same lock (same tx)
      }
      case RECONCILING -> {
        Payment reconciled = payment == null ? null : paymentService.reconcileFromProvider(orgId, payment);
        if (reconciled != null && reconciled.getStatus().isCollectable()) {
          incidentService.markRecovered(orgId, incidentId, reconciled.getAmountMinor(), 0);
          audit.record(
              "PAYMENT_ALREADY_COLLECTED",
              "revenue_incident",
              incidentId.toString(),
              "RECONCILING",
              "RECOVERED",
              null,
              null,
              null);
          return;
        }
        if (reconciled != null && reconciled.getStatus() != PaymentStatus.FAILED) {
          return; // pending — wait for a definitive state
        }
        incident.setStatus(IncidentStatus.DIAGNOSING);
        incidents.save(incident);
        runPipeline(orgId, incidentId);
      }
      case DIAGNOSING -> {
        if (customerOptedOut(incident)) {
          optOut(orgId, incidentId);
          return;
        }
        IncidentDiagnosis diagnosis = diagnosisService.diagnose(orgId, incident, payment);
        incident.setFailureCategory(diagnosis.getFailureCategory());
        incident.setDiagnosisConfidence(diagnosis.getConfidence());
        incident.setDiagnosisLayer(diagnosis.getLayer().name());
        incident.setDiagnosedAt(Instant.now());
        incident.setStatus(IncidentStatus.STRATEGY_SELECTED);
        incidents.save(incident);
        runPipeline(orgId, incidentId);
      }
      case STRATEGY_SELECTED -> {
        if (customerOptedOut(incident)) {
          optOut(orgId, incidentId);
          return;
        }
        if (decisions.findByIncidentId(incidentId).isPresent()) {
          incident.setStatus(IncidentStatus.POLICY_EVALUATING);
          incidents.save(incident);
          runPipeline(orgId, incidentId);
          return;
        }
        selectStrategy(orgId, incident, payment);
        incident.setStatus(IncidentStatus.POLICY_EVALUATING);
        incidents.save(incident);
        runPipeline(orgId, incidentId);
      }
      case POLICY_EVALUATING -> {
        if (customerOptedOut(incident)) {
          optOut(orgId, incidentId);
          return;
        }
        evaluatePolicy(orgId, incident, payment);
      }
      case AWAITING_APPROVAL -> {
        // Waiting for a human. Timeout → EXPIRED.
        if (incident.getRecoveryWindowEndsAt() != null
            && incident.getRecoveryWindowEndsAt().isBefore(Instant.now())) {
          incidentService.transition(orgId, incidentId, IncidentStatus.AWAITING_APPROVAL, IncidentStatus.EXPIRED, "approval window elapsed");
        }
      }
      case SCHEDULED -> {
        // The action scheduler picks up due actions; here we only handle window expiry.
        if (incident.getRecoveryWindowEndsAt() != null
            && incident.getRecoveryWindowEndsAt().isBefore(Instant.now())
            && noPendingActions(incidentId)) {
          incidentService.transition(orgId, incidentId, IncidentStatus.SCHEDULED, IncidentStatus.EXPIRED, "recovery window elapsed");
        }
      }
      case EXECUTING -> {
        // All actions terminal and payment still failed → schedule next attempt or fail.
        if (noPendingActions(incidentId) && !hasSucceededAction(incidentId)) {
          incident.setStatus(IncidentStatus.RETRYABLE_FAILURE);
          incidents.save(incident);
          runPipeline(orgId, incidentId);
        }
      }
      case RETRYABLE_FAILURE -> {
        if (customerOptedOut(incident)) {
          optOut(orgId, incidentId);
          return;
        }
        PolicySet policy = activePolicy(orgId);
        if (incident.getRecoveryWindowEndsAt() != null
            && incident.getRecoveryWindowEndsAt().isBefore(Instant.now())) {
          incidentService.transition(orgId, incidentId, IncidentStatus.RETRYABLE_FAILURE, IncidentStatus.EXPIRED, "recovery window elapsed");
          return;
        }
        if (incident.getAttemptsCount() >= policy.getMaxRetries()) {
          incidentService.transition(orgId, incidentId, IncidentStatus.RETRYABLE_FAILURE, IncidentStatus.FAILED, "max retries reached");
          return;
        }
        incidentService.transition(orgId, incidentId, IncidentStatus.RETRYABLE_FAILURE, IncidentStatus.SCHEDULED, "next attempt");
        scheduleAction(orgId, incident, policy);
      }
      default -> {
        // nothing to advance
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Strategy selection (AI may rank; determinism picks if AI absent/invalid)
  // ---------------------------------------------------------------------------

  private void selectStrategy(UUID orgId, RevenueIncident incident, Payment payment) {
    List<CandidateGenerator.CandidateSeed> seeds = candidateGenerator.generate(incident, payment);
    FailureCategory category = categoryOf(incident, payment);
    boolean businessHours = businessHoursNow();
    DecisionEngine.EvInput input = new DecisionEngine.EvInput(
        incident.getAmountMinor(), category, incident.getIncidentType(),
        payment == null ? null : payment.getPaymentMethod(), businessHours);
    List<CandidateView> scored = decisionEngine.scoreCandidates(seeds, input);

    // AI may rank/explain (validated); deterministic ranking is the default.
    String rankingSource = "DETERMINISTIC";
    List<CandidateView> ranked = scored;
    ObjectNode aiPayload = mapper.createObjectNode();
    aiPayload.set("candidates", mapper.valueToTree(scored));
    JsonNode aiResult = aiClient.call("/v1/rank", aiPayload);
    List<CandidateView> rankedAi = AiRanking.apply(aiResult, scored);
    if (rankedAi != null) {
      ranked = rankedAi;
      rankingSource = "AI";
    }

    RecoveryDecision decision = new RecoveryDecision();
    decision.setOrgId(orgId);
    decision.setIncidentId(incident.getId());
    decision.setCandidates(ranked);
    decision.setChosenStrategy(ranked.get(0).strategy());
    decision.setConfidence(BigDecimal.valueOf(ranked.get(0).probability()));
    decision.setRankingSource(rankingSource);
    decision.setReason(ranked.get(0).rationale());
    decisions.save(decision);
    incident.setSelectedStrategy(ranked.get(0).strategy());

    audit.record(
        "STRATEGY_SELECTED",
        "revenue_incident",
        incident.getId().toString(),
        "DIAGNOSING",
        "STRATEGY_SELECTED",
        audit.json(Map.of(
            "candidates", ranked.stream().map(CandidateView::strategy).toList(),
            "selected", ranked.get(0).strategy(),
            "rankingSource", rankingSource,
            "expectedValueMinor", ranked.get(0).expectedValueMinor())),
        null,
        null);
  }

  // ---------------------------------------------------------------------------
  // Policy gate
  // ---------------------------------------------------------------------------

  private void evaluatePolicy(UUID orgId, RevenueIncident incident, Payment payment) {
    PolicySet policy = activePolicy(orgId);
    String strategy = incident.getSelectedStrategy();
    if (strategy == null || strategy.equals("NO_ACTION")) {
      if (strategy == null || strategy.equals("NO_ACTION")) {
        incidentService.transition(orgId, incident.getId(), IncidentStatus.POLICY_EVALUATING, IncidentStatus.CLOSED, "no action chosen");
      }
      return;
    }

    Evaluation evaluation = policyEngine.evaluate(
        policy,
        payment,
        strategy,
        incident.getAttemptsCount(),
        incident.getContactCount(),
        Instant.now(),
        incident.getAmountMinor());

    if (!evaluation.allowed()) {
      blockedCounter.increment();
      incident.setPolicyResult("BLOCKED:" + evaluation.blockingRule());
      incidents.save(incident);
      incidentService.transition(orgId, incident.getId(), IncidentStatus.POLICY_EVALUATING, IncidentStatus.BLOCKED, "policy blocked: " + evaluation.blockingRule());
      audit.record(
          "POLICY_BLOCKED",
          "revenue_incident",
          incident.getId().toString(),
          "POLICY_EVALUATING",
          "BLOCKED",
          audit.json(evaluation.checks()),
          null,
          null);
      return;
    }

    incident.setPolicyResult("PASS");
    incidents.save(incident);
    audit.record(
        "POLICY_PASSED",
        "revenue_incident",
        incident.getId().toString(),
        "POLICY_EVALUATING",
        null,
        audit.json(evaluation.checks()),
        null,
        null);

    boolean lowConfidence = incident.getDiagnosisConfidence() != null
        && incident.getDiagnosisConfidence().doubleValue() < 0.55;
    if (policyEngine.requiresApproval(policy, incident.getAmountMinor(), strategy, lowConfidence)) {
      requestApproval(orgId, incident, strategy, lowConfidence);
      return;
    }
    incidentService.transition(orgId, incident.getId(), IncidentStatus.POLICY_EVALUATING, IncidentStatus.SCHEDULED, "policy passed");
    scheduleAction(orgId, incident, policy);
  }

  private void requestApproval(UUID orgId, RevenueIncident incident, String strategy, boolean lowConfidence) {
    boolean alreadyPending = approvals.findByIncidentIdAndStatus(incident.getId(), Approval.Status.PENDING).stream()
        .anyMatch(a -> strategy.equals(a.getProposal().path("strategy").asText()));
    if (alreadyPending) {
      incidentService.transition(orgId, incident.getId(), IncidentStatus.POLICY_EVALUATING, IncidentStatus.AWAITING_APPROVAL, "approval already pending");
      return;
    }
    ObjectNode proposal = mapper.createObjectNode();
    proposal.put("strategy", strategy);
    proposal.put("amountMinor", incident.getAmountMinor());
    proposal.put("failureCategory", incident.getFailureCategory());
    proposal.put("confidence", incident.getDiagnosisConfidence() == null ? 0 : incident.getDiagnosisConfidence().doubleValue());
    proposal.put("lowConfidence", lowConfidence);
    proposal.put("requestedAt", Instant.now().toString());
    approvals.save(new Approval(orgId, incident.getId(), proposal));
    incidentService.transition(orgId, incident.getId(), IncidentStatus.POLICY_EVALUATING, IncidentStatus.AWAITING_APPROVAL, "human approval required");
    audit.record(
        "APPROVAL_REQUESTED",
        "revenue_incident",
        incident.getId().toString(),
        "POLICY_EVALUATING",
        "AWAITING_APPROVAL",
        proposal,
        null,
        null);
  }

  // ---------------------------------------------------------------------------
  // Scheduling
  // ---------------------------------------------------------------------------

  @Transactional
  public void scheduleAction(UUID orgId, RevenueIncident incident, PolicySet policy) {
    String strategy = incident.getSelectedStrategy();
    int attemptNumber = incident.getAttemptsCount() + 1;
    String key = RecoveryAction.idempotencyKeyFor(incident.getId(), strategy, attemptNumber);
    if (actions.findByIdempotencyKey(key).isPresent()) {
      return; // idempotent
    }
    Instant scheduledFor = nextExecutionTime(strategy);
    if (incident.getRecoveryWindowEndsAt() != null && scheduledFor.isAfter(incident.getRecoveryWindowEndsAt())) {
      incidentService.transition(orgId, incident.getId(), IncidentStatus.SCHEDULED, IncidentStatus.EXPIRED, "no time left in window");
      return;
    }
    RecoveryAction action = new RecoveryAction(orgId, incident.getId(), strategy, attemptNumber, scheduledFor, key);
    actions.save(action);
    incident.setNextActionAt(scheduledFor);
    incidents.save(incident);
    // Durable timer: Temporal workflow when enabled; DB scheduler otherwise.
    workflowLauncher.launchRecovery(action);
    audit.record(
        "ACTION_SCHEDULED",
        "recovery_action",
        action.getId().toString(),
        incident.getId(),
        null,
        "SCHEDULED",
        audit.json(Map.of("strategy", strategy, "attempt", attemptNumber, "scheduledFor", scheduledFor.toString(), "idempotencyKey", key)),
        null,
        null);
  }

  private Instant nextExecutionTime(String strategy) {
    Instant now = Instant.now();
    return switch (strategy) {
      case "DELAYED_RETRY" -> nextBusinessHour(now);
      case "PAYMENT_LINK", "UPI_RECOVERY", "EMAIL_NUDGE", "WHATSAPP_NUDGE", "SMS_NUDGE", "BOUNDED_DISCOUNT" ->
          now.plus(Duration.ofMinutes(2)); // demo-friendly; production configurable
      default -> now.plus(Duration.ofHours(1));
    };
  }

  private static Instant nextBusinessHour(Instant now) {
    ZonedDateTime kolkata = now.atZone(ZoneId.of("Asia/Kolkata"));
    ZonedDateTime next = kolkata.plusHours(6).withMinute(30).withSecond(0).withNano(0);
    if (next.getHour() > 21) {
      next = next.plusDays(1).withHour(10).withMinute(0);
    } else if (next.getHour() < 9) {
      next = next.withHour(10).withMinute(0);
    }
    return next.toInstant();
  }

  // ---------------------------------------------------------------------------
  // Execution (called by the action scheduler; re-validates everything)
  // ---------------------------------------------------------------------------

  @Transactional
  public void executeAction(UUID orgId, UUID actionId) {
    RecoveryAction action = actions
        .findByIdForUpdate(actionId)
        .orElseThrow(() -> com.recoverai.common.api.ApiException.notFound("Action not found"));
    if (action.getStatus() != Status.SCHEDULED) {
      return; // idempotent: already executed/cancelled/blocked
    }
    RevenueIncident incident = incidents
        .findByIdForUpdate(action.getIncidentId())
        .orElseThrow(() -> com.recoverai.common.api.ApiException.notFound("Incident not found"));
    if (!incident.getOrgId().equals(orgId) || incident.getStatus().isTerminal()) {
      cancelAction(action, "incident not actionable");
      return;
    }

    // 1. Reconcile current payment state BEFORE anything that could take money.
    Payment payment = incident.getPaymentId() == null
        ? null
        : payments.findById(incident.getPaymentId()).orElse(null);
    if (payment != null) {
      payment = paymentService.reconcileFromProvider(orgId, payment);
      if (payment.getStatus().isCollectable()) {
        handleLateAuthorization(orgId, incident, action, payment);
        return;
      }
      if (payment.getStatus() != PaymentStatus.FAILED) {
        return; // still pending — keep waiting
      }
    }

    // 2. Policy re-check with current state.
    PolicySet policy = activePolicy(orgId);
    Evaluation evaluation = policyEngine.evaluate(
        policy, payment, action.getStrategy(), incident.getAttemptsCount(),
        incident.getContactCount(), Instant.now(), incident.getAmountMinor());
    if (!evaluation.allowed()) {
      action.setStatus(Status.BLOCKED);
      action.setError("policy: " + evaluation.blockingRule());
      actions.save(action);
      blockedCounter.increment();
      incidentService.transitionBestEffort(orgId, incident.getId(), IncidentStatus.BLOCKED, "policy blocked at execution: " + evaluation.blockingRule());
      audit.record("POLICY_BLOCKED", "recovery_action", actionId.toString(), incident.getId(), "SCHEDULED", "BLOCKED", audit.json(evaluation.checks()), null, null);
      return;
    }
    if (incident.getRecoveryWindowEndsAt() != null
        && incident.getRecoveryWindowEndsAt().isBefore(Instant.now())) {
      cancelAction(action, "recovery window expired");
      incidentService.transitionBestEffort(orgId, incident.getId(), IncidentStatus.EXPIRED, "recovery window expired");
      return;
    }
    if (customerOptedOut(incident)) {
      cancelAction(action, "customer opted out");
      optOut(orgId, incident.getId());
      return;
    }

    // 3. Execute.
    action.setStatus(Status.EXECUTING);
    actions.save(action);
    incident.setStatus(IncidentStatus.EXECUTING);
    incidents.save(incident);
    audit.record(
        "ACTION_EXECUTING",
        "recovery_action",
        actionId.toString(),
        incident.getId(),
        "SCHEDULED",
        "EXECUTING",
        audit.json(Map.of("strategy", action.getStrategy(), "attempt", action.getAttemptNumber())),
        null,
        null);

    try {
      String result = dispatch(action, incident);
      action.setStatus(Status.SUCCEEDED);
      action.setResult(result);
      action.setExecutedAt(Instant.now());
      actions.save(action);

      incident.setAttemptsCount(incident.getAttemptsCount() + 1);
      incidents.save(incident);

      attempts.save(new RecoveryAttempt(orgId, incident.getId(), action.getId(), action.getAttemptNumber(), action.getStrategy(), "SUCCEEDED"));
      audit.record(
          "ACTION_EXECUTED",
          "recovery_action",
          actionId.toString(),
          incident.getId(),
          "EXECUTING",
          "SUCCEEDED",
          audit.json(nullSafeMap(
              "strategy", action.getStrategy(),
              "result", result,
              "providerReference", action.getProviderReference())),
          null,
          null);
    } catch (ProviderException e) {
      action.setStatus(Status.FAILED);
      action.setError(truncate(e.getMessage(), 400));
      action.setExecutedAt(Instant.now());
      actions.save(action);
      attempts.save(new RecoveryAttempt(orgId, incident.getId(), action.getId(), action.getAttemptNumber(), action.getStrategy(), "FAILED"));
      audit.record("ACTION_FAILED", "recovery_action", actionId.toString(), incident.getId(), "EXECUTING", "FAILED", null, audit.json(Map.of("error", e.getMessage(), "category", e.category())), null);

      boolean transientFailure = e.category().equals("TRANSIENT") || e.category().equals("RATE_LIMITED");
      if (transientFailure && incident.getAttemptsCount() < policy.getMaxRetries()) {
        incidentService.transitionBestEffort(orgId, incident.getId(), IncidentStatus.RETRYABLE_FAILURE, "transient provider failure: " + e.getMessage());
        runPipeline(orgId, incident.getId());
      } else {
        incidentService.transitionBestEffort(orgId, incident.getId(), IncidentStatus.FAILED, "provider failure: " + e.getMessage());
      }
    }
  }

  /** Executes the strategy via the real adapter (mock in demo mode, labeled SIMULATED). */
  private String dispatch(RecoveryAction action, RevenueIncident incident) throws ProviderException {
    long amount = incident.getAmountMinor();
    switch (action.getStrategy()) {
      case "PAYMENT_LINK", "UPI_RECOVERY" -> {
        PaymentLink link = provider.createPaymentLink(
            amount,
            incident.getCurrency(),
            "Pending payment recovery — " + incident.getSelectedStrategy(),
            customerEmail(incident),
            customerPhone(incident),
            incident.getId().toString());
        action.setProviderReference(link.id());
        action.setProviderResponse(mapper.valueToTree(link.raw()));
        if (incident.getCustomerId() != null) {
          PolicySet policy = activePolicy(incident.getOrgId());
          communicationService.sendRecoveryMessage(
              incident.getOrgId(),
              incident.getId(),
              incident.getCustomerId(),
              Channel.DEMO_INBOX,
              "payment_link_ready",
              Map.of(
                  "customerName", customerName(incident),
                  "merchantName", "RecoverAI Demo",
                  "amount", formatAmount(amount, incident.getCurrency()),
                  "paymentLink", link.shortUrl() == null ? "(demo link)" : link.shortUrl()),
              incident.getContactCount(),
              policy.getMaxContactAttempts(),
              Duration.ofHours(policy.getContactCooldownHours()));
          incident.setContactCount(incident.getContactCount() + 1);
          incidents.save(incident);
        }
        return link.shortUrl() == null ? "PAYMENT_LINK_CREATED" : "PAYMENT_LINK_CREATED";
      }
      case "DELAYED_RETRY" -> {
        ProviderOrder order = provider.createOrder(amount, incident.getCurrency(), "recoverai-" + incident.getId().toString().substring(0, 8));
        action.setProviderReference(order.id());
        action.setProviderResponse(mapper.valueToTree(order.raw()));
        return "ORDER_CREATED";
      }
      case "EMAIL_NUDGE", "WHATSAPP_NUDGE", "SMS_NUDGE" -> {
        if (incident.getCustomerId() != null) {
          PolicySet policy = activePolicy(incident.getOrgId());
          communicationService.sendRecoveryMessage(
              incident.getOrgId(),
              incident.getId(),
              incident.getCustomerId(),
              Channel.DEMO_INBOX,
              "payment_failed_gentle",
              Map.of("customerName", customerName(incident), "merchantName", "RecoverAI Demo", "amount", formatAmount(amount, incident.getCurrency())),
              incident.getContactCount(),
              policy.getMaxContactAttempts(),
              Duration.ofHours(policy.getContactCooldownHours()));
          incident.setContactCount(incident.getContactCount() + 1);
          incidents.save(incident);
        }
        return "MESSAGE_SENT";
      }
      case "BOUNDED_DISCOUNT" -> {
        if (incident.getCustomerId() != null) {
          PolicySet policy = activePolicy(incident.getOrgId());
          communicationService.sendRecoveryMessage(
              incident.getOrgId(),
              incident.getId(),
              incident.getCustomerId(),
              Channel.DEMO_INBOX,
              "discount_incentive",
              Map.of("customerName", customerName(incident), "merchantName", "RecoverAI Demo", "amount", formatAmount(amount, incident.getCurrency())),
              incident.getContactCount(),
              policy.getMaxContactAttempts(),
              Duration.ofHours(policy.getContactCooldownHours()));
          incident.setContactCount(incident.getContactCount() + 1);
          incidents.save(incident);
        }
        return "DISCOUNT_OFFERED";
      }
      case "WAIT_FOR_PROVIDER_RETRY" -> {
        return "WAITING";
      }
      default -> {
        return "NOOP";
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Late authorization — the mandatory double-collection protection
  // ---------------------------------------------------------------------------

  @Transactional
  public void handleLateAuthorization(UUID orgId, RevenueIncident incident, RecoveryAction action, Payment payment) {
    lateAuthCounter.increment();
    audit.record(
        "LATE_AUTHORIZATION_RECEIVED",
        "payment",
        payment.getId().toString(),
        incident.getId(),
        "FAILED",
        payment.getStatus().name(),
        audit.json(nullSafeMap("paymentId", payment.getId().toString(), "providerPaymentId", payment.getProviderPaymentId())),
        null,
        null);
    if (action != null) {
      cancelAction(action, "payment became " + payment.getStatus() + " before recovery execution");
    }
    // Cancel any other pending actions too.
    actions.findByIncidentIdOrderByCreatedAtAsc(incident.getId()).stream()
        .filter(a -> a.getStatus() == Status.SCHEDULED || a.getStatus() == Status.EXECUTING)
        .filter(a -> !a.getId().equals(action == null ? null : action.getId()))
        .forEach(a -> cancelAction(a, "payment became " + payment.getStatus()));
    incidentService.transitionBestEffort(orgId, incident.getId(), IncidentStatus.LATE_AUTHORIZED, "payment became " + payment.getStatus() + " before recovery execution");
    audit.record(
        "RECOVERY_CANCELLED",
        "revenue_incident",
        incident.getId().toString(),
        null,
        "LATE_AUTHORIZED",
        audit.json(Map.of("reason", "Payment became authorized before recovery execution. Duplicate collection prevented.", "paymentStatus", payment.getStatus().name())),
        null,
        null);
  }

  /** Payment captured/authorized — incident is resolved (money safe). */
  @Transactional
  public void onPaymentCollected(UUID orgId, Payment payment) {
    Optional<RevenueIncident> open = incidentService.findOpenIncidentForPayment(orgId, payment.getId());
    if (open.isEmpty()) {
      return;
    }
    RevenueIncident incident = open.get();
    if (payment.getStatus() == PaymentStatus.CAPTURED) {
      actions.findByIncidentIdOrderByCreatedAtAsc(incident.getId()).stream()
          .filter(a -> a.getStatus() == Status.SCHEDULED || a.getStatus() == Status.EXECUTING)
          .forEach(a -> cancelAction(a, "payment captured"));
      incidentService.markRecovered(orgId, incident.getId(), payment.getAmountMinor(), incident.getInterventionCostMinor());
      recoveredCounter.increment();
      audit.record(
          "PAYMENT_RECOVERED",
          "revenue_incident",
          incident.getId().toString(),
          incident.getStatus().name(),
          "RECOVERED",
          audit.json(Map.of("amountMinor", payment.getAmountMinor())),
          null,
          null);
    } else if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
      // Authorized but not yet captured: money is safe; cancel recovery to avoid
      // any chance of duplicate collection while capture completes.
      actions.findByIncidentIdOrderByCreatedAtAsc(incident.getId()).stream()
          .filter(a -> a.getStatus() == Status.SCHEDULED || a.getStatus() == Status.EXECUTING)
          .forEach(a -> cancelAction(a, "payment authorized"));
      incidentService.transitionBestEffort(orgId, incident.getId(), IncidentStatus.LATE_AUTHORIZED, "payment authorized before recovery execution");
      audit.record(
          "LATE_AUTHORIZATION_RECEIVED",
          "revenue_incident",
          incident.getId().toString(),
          null,
          "LATE_AUTHORIZED",
          audit.json(Map.of("reason", "Payment became authorized before recovery execution. Duplicate collection prevented.")),
          null,
          null);
    }
  }

  /** Customer opted out — hard stop. */
  @Transactional
  public void optOut(UUID orgId, UUID incidentId) {
    actions.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
        .filter(a -> a.getStatus() == Status.SCHEDULED || a.getStatus() == Status.EXECUTING)
        .forEach(a -> cancelAction(a, "customer opted out"));
    incidentService.transitionBestEffort(orgId, incidentId, IncidentStatus.OPTED_OUT, "customer opted out");
    audit.record("CUSTOMER_OPTED_OUT", "revenue_incident", incidentId.toString(), null, "OPTED_OUT", null, null, null);
  }

  /** Cancel all pending actions for an incident (e.g. approval rejected). */
  @Transactional
  public void cancelPendingActions(UUID orgId, UUID incidentId, String reason) {
    actions.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
        .filter(a -> a.getStatus() == Status.SCHEDULED || a.getStatus() == Status.EXECUTING)
        .forEach(a -> cancelAction(a, reason));
  }

  @Transactional
  public void cancelAction(RecoveryAction action, String reason) {
    if (action.getStatus() == Status.SCHEDULED || action.getStatus() == Status.EXECUTING) {
      action.setStatus(Status.CANCELLED);
      action.setError(reason);
      actions.save(action);
      audit.record(
          "ACTION_CANCELLED",
          "recovery_action",
          action.getId().toString(),
          action.getIncidentId(),
          null,
          "CANCELLED",
          audit.json(Map.of("reason", reason, "idempotencyKey", action.getIdempotencyKey())),
          null,
          null);
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private PolicySet activePolicy(UUID orgId) {
    return policySets
        .findByOrgIdAndActiveTrue(orgId)
        .orElseGet(() -> {
          PolicySet fallback = new PolicySet(orgId, "default");
          fallback.setActive(true);
          return policySets.save(fallback);
        });
  }

  private boolean customerOptedOut(RevenueIncident incident) {
    if (incident.getCustomerId() == null) {
      return false;
    }
    return customers.findById(incident.getCustomerId()).map(Customer::optedOut).orElse(false);
  }

  private boolean noPendingActions(UUID incidentId) {
    long pending = actions.countByIncidentIdAndStatusIn(
        incidentId, List.of(Status.SCHEDULED, Status.EXECUTING));
    return pending == 0;
  }

  private boolean hasSucceededAction(UUID incidentId) {
    return actions.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
        .anyMatch(a -> a.getStatus() == Status.SUCCEEDED);
  }

  private FailureCategory categoryOf(RevenueIncident incident, Payment payment) {
    if (incident.getFailureCategory() != null) {
      try {
        return FailureCategory.valueOf(incident.getFailureCategory());
      } catch (IllegalArgumentException ignored) {
        // fall through
      }
    }
    if (payment != null && payment.getFailureCategory() != null) {
      return payment.getFailureCategory();
    }
    return FailureCategory.UNKNOWN;
  }

  private static boolean businessHoursNow() {
    int hour = java.time.ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).getHour();
    return hour >= 9 && hour <= 21;
  }

  private String customerEmail(RevenueIncident incident) {
    return incident.getCustomerId() == null ? null : customers.findById(incident.getCustomerId()).map(Customer::getEmail).orElse(null);
  }

  private String customerPhone(RevenueIncident incident) {
    return incident.getCustomerId() == null ? null : customers.findById(incident.getCustomerId()).map(Customer::getPhone).orElse(null);
  }

  private String customerName(RevenueIncident incident) {
    return incident.getCustomerId() == null
        ? "there"
        : customers.findById(incident.getCustomerId()).map(Customer::getFullName).orElse("there");
  }

  private static String formatAmount(long amountMinor, String currency) {
    return "₹" + (amountMinor / 100) + (amountMinor % 100 == 0 ? "" : "." + String.format("%02d", amountMinor % 100));
  }

  /** Map.of equivalent that tolerates null values (audit snapshots). */
  private static Map<String, Object> nullSafeMap(Object... kv) {
    java.util.HashMap<String, Object> map = new java.util.HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      map.put((String) kv[i], kv[i + 1]);
    }
    return map;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
