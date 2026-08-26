package com.recoverai.incident.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.audit.application.AuditService;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.api.ErrorCode;
import com.recoverai.incident.domain.IncidentStateMachine;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.IncidentType;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.policy.domain.PolicySet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Incident lifecycle: creation and validated, audited, lock-protected state transitions.
 * Every transition writes an audit event with before/after state. Concurrent workers
 * converge because transitions take a pessimistic row lock (single writer per incident).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {

  private final RevenueIncidentRepository repository;
  private final AuditService audit;

  @Transactional
  public RevenueIncident createPaymentFailureIncident(
      UUID orgId,
      UUID merchantId,
      UUID customerId,
      UUID paymentId,
      long amountMinor,
      String currency,
      String failureCategory,
      PolicySet policy) {

    RevenueIncident incident = new RevenueIncident();
    incident.setOrgId(orgId);
    incident.setMerchantId(merchantId);
    incident.setCustomerId(customerId);
    incident.setPaymentId(paymentId);
    incident.setIncidentType(IncidentType.PAYMENT_FAILURE);
    incident.setStatus(IncidentStatus.DETECTED);
    incident.setAmountMinor(amountMinor);
    incident.setCurrency(currency);
    incident.setFailureCategory(failureCategory);
    incident.setRecoveryWindowEndsAt(Instant.now().plus(java.time.Duration.ofHours(policy.getRecoveryWindowHours())));
    RevenueIncident saved = repository.save(incident);

    audit.record(
        "INCIDENT_CREATED",
        "revenue_incident",
        saved.getId().toString(),
        null,
        "DETECTED",
        audit.json(java.util.Map.of("amountMinor", amountMinor, "currency", currency, "failureCategory", failureCategory, "type", "PAYMENT_FAILURE")),
        null,
        null);
    return saved;
  }

  @Transactional
  public RevenueIncident createSubscriptionFailureIncident(
      UUID orgId,
      UUID merchantId,
      UUID customerId,
      UUID subscriptionId,
      UUID paymentId,
      long amountMinor,
      String currency,
      String failureCategory,
      PolicySet policy) {
    RevenueIncident incident = new RevenueIncident();
    incident.setOrgId(orgId);
    incident.setMerchantId(merchantId);
    incident.setCustomerId(customerId);
    incident.setSubscriptionId(subscriptionId);
    incident.setPaymentId(paymentId);
    incident.setIncidentType(IncidentType.SUBSCRIPTION_FAILURE);
    incident.setStatus(IncidentStatus.DETECTED);
    incident.setAmountMinor(amountMinor);
    incident.setCurrency(currency);
    incident.setFailureCategory(failureCategory);
    incident.setRecoveryWindowEndsAt(Instant.now().plus(java.time.Duration.ofHours(policy.getRecoveryWindowHours())));
    RevenueIncident saved = repository.save(incident);
    audit.record(
        "INCIDENT_CREATED",
        "revenue_incident",
        saved.getId().toString(),
        null,
        "DETECTED",
        audit.json(java.util.Map.of("amountMinor", amountMinor, "type", "SUBSCRIPTION_FAILURE")),
        null,
        null);
    return saved;
  }

  @Transactional
  public RevenueIncident createCheckoutAbandonmentIncident(
      UUID orgId,
      UUID merchantId,
      UUID customerId,
      UUID checkoutSessionId,
      long amountMinor,
      String currency,
      PolicySet policy) {
    RevenueIncident incident = new RevenueIncident();
    incident.setOrgId(orgId);
    incident.setMerchantId(merchantId);
    incident.setCustomerId(customerId);
    incident.setCheckoutSessionId(checkoutSessionId);
    incident.setIncidentType(IncidentType.CHECKOUT_ABANDONMENT);
    incident.setStatus(IncidentStatus.DETECTED);
    incident.setAmountMinor(amountMinor);
    incident.setCurrency(currency);
    incident.setFailureCategory("CHECKOUT_ABANDONED");
    incident.setRecoveryWindowEndsAt(Instant.now().plus(java.time.Duration.ofHours(policy.getRecoveryWindowHours())));
    RevenueIncident saved = repository.save(incident);
    audit.record(
        "INCIDENT_CREATED",
        "revenue_incident",
        saved.getId().toString(),
        null,
        "DETECTED",
        audit.json(java.util.Map.of("amountMinor", amountMinor, "type", "CHECKOUT_ABANDONMENT")),
        null,
        null);
    return saved;
  }

  /**
   * Validate + persist a state transition. Row is locked for the duration of the
   * transition so concurrent workers cannot double-advance; the audit event is written
   * in the same transaction.
   */
  @Transactional
  public RevenueIncident transition(UUID orgId, UUID incidentId, IncidentStatus from, IncidentStatus to, String reason) {
    RevenueIncident incident = repository
        .findByIdForUpdate(incidentId)
        .orElseThrow(() -> ApiException.notFound("Incident not found"));

    if (!incident.getOrgId().equals(orgId)) {
      throw ApiException.notFound("Incident not found");
    }
    if (incident.getStatus() != from) {
      throw new ApiException(
          ErrorCode.INCIDENT_STATE_INVALID,
          "Expected incident state " + from + " but was " + incident.getStatus(),
          409);
    }

    IncidentStatus next = IncidentStateMachine.transition(from, to);
    incident.setStatus(next);
    applyTimestamps(incident, next, reason);
    RevenueIncident saved = repository.save(incident);

    audit.record(
        "INCIDENT_TRANSITION",
        "revenue_incident",
        saved.getId().toString(),
        from.name(),
        next.name(),
        null,
        audit.json(java.util.Map.of("reason", reason == null ? "" : reason)),
        null);
    return saved;
  }

  /** Best-effort transition when the caller doesn't know the exact current state. */
  @Transactional
  public RevenueIncident transitionBestEffort(UUID orgId, UUID incidentId, IncidentStatus to, String reason) {
    RevenueIncident incident = repository
        .findByIdForUpdate(incidentId)
        .orElseThrow(() -> ApiException.notFound("Incident not found"));
    if (!incident.getOrgId().equals(orgId)) {
      throw ApiException.notFound("Incident not found");
    }
    if (incident.getStatus() == to) {
      return incident;
    }
    IncidentStatus next = IncidentStateMachine.transition(incident.getStatus(), to);
    incident.setStatus(next);
    applyTimestamps(incident, next, reason);
    RevenueIncident saved = repository.save(incident);
    audit.record(
        "INCIDENT_TRANSITION",
        "revenue_incident",
        saved.getId().toString(),
        incident.getStatus().name(),
        next.name(),
        null,
        audit.json(java.util.Map.of("reason", reason == null ? "" : reason)),
        null);
    return saved;
  }

  private void applyTimestamps(RevenueIncident incident, IncidentStatus status, String reason) {
    Instant now = Instant.now();
    switch (status) {
      case SCHEDULED -> incident.setScheduledAt(now);
      case EXECUTING -> incident.setExecutedAt(now);
      case RECOVERED -> {
        incident.setRecoveredAt(now);
        incident.setCancellationReason(null);
      }
      case LATE_AUTHORIZED, CANCELLED, BLOCKED -> incident.setCancellationReason(reason);
      case CLOSED -> incident.setClosedAt(now);
      case FAILED -> incident.setCancellationReason(reason);
      default -> {}
    }
    incident.setUpdatedAt(now);
  }

  @Transactional(readOnly = true)
  public RevenueIncident get(UUID orgId, UUID incidentId) {
    return repository
        .findByOrgIdAndId(orgId, incidentId)
        .orElseThrow(() -> ApiException.notFound("Incident not found"));
  }

  @Transactional
  public void markRecovered(UUID orgId, UUID incidentId, long recoveredAmountMinor, long interventionCostMinor) {
    RevenueIncident incident = repository
        .findByIdForUpdate(incidentId)
        .orElseThrow(() -> ApiException.notFound("Incident not found"));
    if (incident.getStatus() == IncidentStatus.RECOVERED || incident.getStatus() == IncidentStatus.CLOSED) {
      return; // idempotent
    }
    IncidentStatus next = IncidentStateMachine.transition(incident.getStatus(), IncidentStatus.RECOVERED);
    incident.setStatus(next);
    incident.setRecoveredAmountMinor(recoveredAmountMinor);
    incident.setInterventionCostMinor(interventionCostMinor);
    incident.setNetRecoveredMinor(Math.max(0, recoveredAmountMinor - interventionCostMinor));
    incident.setRecoveredAt(Instant.now());
    incident.setUpdatedAt(Instant.now());
    repository.save(incident);
    audit.record(
        "PAYMENT_RECOVERED",
        "revenue_incident",
        incidentId.toString(),
        null,
        "RECOVERED",
        audit.json(java.util.Map.of("recoveredAmountMinor", recoveredAmountMinor, "interventionCostMinor", interventionCostMinor)),
        null,
        null);
  }

  public java.util.Optional<RevenueIncident> findOpenIncidentForPayment(UUID orgId, UUID paymentId) {
    return repository
        .findByOrgIdAndPaymentId(orgId, paymentId)
        .filter(i -> !i.getStatus().isTerminal());
  }

  public java.util.Optional<RevenueIncident> findOpenIncidentForSubscription(UUID orgId, UUID subscriptionId) {
    if (subscriptionId == null) {
      return java.util.Optional.empty();
    }
    return repository.findByOrgIdAndSubscriptionId(orgId, subscriptionId).stream()
        .filter(i -> !i.getStatus().isTerminal())
        .findFirst();
  }

  public java.util.Optional<RevenueIncident> findOpenIncidentForCheckout(UUID orgId, UUID checkoutSessionId) {
    return repository
        .findByOrgIdAndCheckoutSessionId(orgId, checkoutSessionId)
        .filter(i -> !i.getStatus().isTerminal());
  }
}
