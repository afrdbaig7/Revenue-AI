package com.recoverai.incident.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.approval.domain.Approval;
import com.recoverai.approval.infrastructure.ApprovalRepository;
import com.recoverai.audit.infrastructure.AuditEventRepository;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.api.PageResponse;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.communication.domain.Communication;
import com.recoverai.communication.infrastructure.CommunicationRepository;
import com.recoverai.diagnosis.infrastructure.IncidentDiagnosisRepository;
import com.recoverai.incident.application.IncidentService;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.infrastructure.PaymentRepository;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import com.recoverai.promise.application.PromiseService;
import com.recoverai.promise.domain.PromiseToPay;
import com.recoverai.promise.infrastructure.PromiseToPayRepository;
import com.recoverai.recovery.application.RecoveryOrchestrator;
import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.domain.RecoveryDecision;
import com.recoverai.recovery.infrastructure.RecoveryActionRepository;
import com.recoverai.recovery.infrastructure.RecoveryAttemptRepository;
import com.recoverai.recovery.infrastructure.RecoveryDecisionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Incident list, detail (full explainability timeline), and operator actions. */
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

  private final RevenueIncidentRepository incidents;
  private final IncidentService incidentService;
  private final RecoveryOrchestrator orchestrator;
  private final RecoveryDecisionRepository decisions;
  private final RecoveryActionRepository actions;
  private final RecoveryAttemptRepository attempts;
  private final IncidentDiagnosisRepository diagnoses;
  private final AuditEventRepository auditEvents;
  private final CommunicationRepository communications;
  private final PromiseToPayRepository promises;
  private final ApprovalRepository approvals;
  private final PaymentRepository payments;
  private final PolicySetRepository policySets;
  private final PromiseService promiseService;
  private final ObjectMapper mapper;

  @GetMapping
  public PageResponse<Map<String, Object>> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String failureCategory,
      @RequestParam(required = false) String strategy,
      @RequestParam(required = false) Long minAmountMinor,
      @RequestParam(required = false) Long maxAmountMinor) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    Specification<RevenueIncident> spec = (root, query, cb) -> {
      var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
      predicates.add(cb.equal(root.get("orgId"), orgId));
      if (status != null && !status.isBlank()) {
        predicates.add(cb.equal(root.get("status"), IncidentStatus.valueOf(status)));
      }
      if (failureCategory != null && !failureCategory.isBlank()) {
        predicates.add(cb.equal(root.get("failureCategory"), failureCategory));
      }
      if (strategy != null && !strategy.isBlank()) {
        predicates.add(cb.equal(root.get("selectedStrategy"), strategy));
      }
      if (minAmountMinor != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("amountMinor"), minAmountMinor));
      }
      if (maxAmountMinor != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("amountMinor"), maxAmountMinor));
      }
      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
    Page<RevenueIncident> result =
        incidents.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
    return PageResponse.of(
        result.getContent().stream().map(this::toListRow).toList(),
        page,
        Math.min(size, 100),
        result.getTotalElements());
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(Authentication authentication, @PathVariable UUID id) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    RevenueIncident incident = incidentService.get(user.orgId(), id);
    Map<String, Object> body = new HashMap<>();
    body.put("incident", toDetail(incident));
    body.put("payment", incident.getPaymentId() == null ? null : payments.findById(incident.getPaymentId()).orElse(null));
    body.put("diagnoses", diagnoses.findByOrgIdAndIncidentIdOrderByCreatedAtDesc(user.orgId(), id));
    body.put("decision", decisions.findByIncidentId(id).orElse(null));
    body.put("actions", actions.findByIncidentIdOrderByCreatedAtAsc(id));
    body.put("attempts", attempts.findByIncidentIdOrderByOccurredAtAsc(id));
    body.put("communications", communications.findByIncidentIdOrderByCreatedAtAsc(id));
    body.put("promises", promises.findByOrgIdAndStatusIn(user.orgId(), List.of(PromiseToPay.Status.values())));
    body.put("approvals", approvals.findByIncidentIdAndStatus(id, Approval.Status.PENDING));
    return body;
  }

  /** Re-drive the recovery pipeline for an incident (operator/admin). */
  @PostMapping("/{id}/reprocess")
  public Map<String, String> reprocess(Authentication authentication, @PathVariable UUID id) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    orchestrator.runPipeline(user.orgId(), id);
    return Map.of("status", "ok", "incidentId", id.toString());
  }

  @PostMapping("/{id}/cancel")
  public Map<String, String> cancel(Authentication authentication, @PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    RevenueIncident incident = incidentService.get(user.orgId(), id);
    if (!incident.getStatus().isTerminal()) {
      orchestrator.cancelPendingActions(user.orgId(), id, req == null ? "manually cancelled" : req.reason());
      incidentService.transitionBestEffort(user.orgId(), id, IncidentStatus.CANCELLED, req == null ? "manually cancelled" : req.reason());
    }
    return Map.of("status", "cancelled", "incidentId", id.toString());
  }

  /** Promise-to-pay from natural language ("Salary comes Monday"). */
  @PostMapping("/{id}/promise")
  public Map<String, Object> promise(
      Authentication authentication, @PathVariable UUID id, @Valid @RequestBody PromiseRequest req) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    RevenueIncident incident = incidentService.get(user.orgId(), id);
    if (incident.getCustomerId() == null) {
      throw ApiException.badRequest("Incident has no customer to attach the promise to");
    }
    PromiseService.PromiseIntent intent = promiseService.extract(req.text(), ZoneId.of("Asia/Kolkata"));
    PromiseToPay promise = promiseService.create(
        user.orgId(), id, incident.getCustomerId(), req.amountMinor() == null ? incident.getAmountMinor() : req.amountMinor(),
        incident.getCurrency(), intent);
    Map<String, Object> body = new HashMap<>();
    body.put("promise", promise);
    body.put("extracted", intent);
    return body;
  }

  private Map<String, Object> toListRow(RevenueIncident i) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", i.getId());
    row.put("incidentType", i.getIncidentType());
    row.put("status", i.getStatus());
    row.put("amountMinor", i.getAmountMinor());
    row.put("currency", i.getCurrency());
    row.put("failureCategory", i.getFailureCategory());
    row.put("confidence", i.getDiagnosisConfidence());
    row.put("diagnosisLayer", i.getDiagnosisLayer());
    row.put("selectedStrategy", i.getSelectedStrategy());
    row.put("attemptsCount", i.getAttemptsCount());
    row.put("contactCount", i.getContactCount());
    row.put("recoveredAmountMinor", i.getRecoveredAmountMinor());
    row.put("createdAt", i.getCreatedAt());
    row.put("nextActionAt", i.getNextActionAt());
    row.put("customerId", i.getCustomerId());
    return row;
  }

  private Map<String, Object> toDetail(RevenueIncident i) {
    Map<String, Object> row = toListRow(i);
    row.put("detectedAt", i.getDetectedAt());
    row.put("diagnosedAt", i.getDiagnosedAt());
    row.put("recoveredAt", i.getRecoveredAt());
    row.put("closedAt", i.getClosedAt());
    row.put("recoveryWindowEndsAt", i.getRecoveryWindowEndsAt());
    row.put("cancellationReason", i.getCancellationReason());
    row.put("policyResult", i.getPolicyResult());
    row.put("netRecoveredMinor", i.getNetRecoveredMinor());
    row.put("interventionCostMinor", i.getInterventionCostMinor());
    row.put("evidenceSummary", i.getEvidenceSummary());
    row.put("paymentId", i.getPaymentId());
    row.put("subscriptionId", i.getSubscriptionId());
    row.put("checkoutSessionId", i.getCheckoutSessionId());
    return row;
  }

  public record CancelRequest(String reason) {}

  public record PromiseRequest(@NotBlank String text, Long amountMinor) {}
}
