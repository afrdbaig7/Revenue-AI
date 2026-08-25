package com.recoverai.approval.application;

import com.recoverai.approval.domain.Approval;
import com.recoverai.approval.infrastructure.ApprovalRepository;
import com.recoverai.audit.application.AuditService;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.incident.application.IncidentService;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import com.recoverai.recovery.application.RecoveryOrchestrator;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human-in-the-loop approval flow. Approvals are scoped to org + incident, record who
 * decided and when, and drive the incident forward only when approved. Rejection
 * cancels the recovery.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

  private final ApprovalRepository approvals;
  private final RevenueIncidentRepository incidents;
  private final PolicySetRepository policySets;
  private final IncidentService incidentService;
  private final RecoveryOrchestrator orchestrator;
  private final AuditService audit;

  @Transactional
  public Approval approve(UUID orgId, UUID approvalId, CurrentUser actor, String note) {
    Approval approval = getScoped(orgId, approvalId);
    if (approval.getStatus() != Approval.Status.PENDING) {
      throw ApiException.conflict("Approval already decided: " + approval.getStatus());
    }
    RevenueIncident incident = incidents
        .findByIdForUpdate(approval.getIncidentId())
        .orElseThrow(() -> ApiException.notFound("Incident not found"));

    approval.setStatus(Approval.Status.APPROVED);
    approval.setDecidedBy(actor.userId());
    approval.setDecidedAt(Instant.now());
    approval.setDecisionNote(note);
    approvals.save(approval);

    if (incident.getStatus() == IncidentStatus.AWAITING_APPROVAL) {
      PolicySet policy = policySets.findByOrgIdAndActiveTrue(orgId).orElse(null);
      incidentService.transitionBestEffort(orgId, incident.getId(), IncidentStatus.SCHEDULED, "approved");
      orchestrator.scheduleAction(orgId, incident, policy);
    }
    audit.record(
        "APPROVED",
        "approval",
        approval.getId().toString(),
        approval.getIncidentId(),
        "PENDING",
        "APPROVED",
        audit.json(java.util.Map.of("actor", actor.userId().toString(), "incidentId", approval.getIncidentId().toString())),
        null,
        null);
    return approval;
  }

  @Transactional
  public Approval reject(UUID orgId, UUID approvalId, CurrentUser actor, String note) {
    Approval approval = getScoped(orgId, approvalId);
    if (approval.getStatus() != Approval.Status.PENDING) {
      throw ApiException.conflict("Approval already decided: " + approval.getStatus());
    }
    approval.setStatus(Approval.Status.REJECTED);
    approval.setDecidedBy(actor.userId());
    approval.setDecidedAt(Instant.now());
    approval.setDecisionNote(note);
    approvals.save(approval);

    RevenueIncident incident = incidents
        .findByIdForUpdate(approval.getIncidentId())
        .orElseThrow(() -> ApiException.notFound("Incident not found"));
    if (incident.getStatus() == IncidentStatus.AWAITING_APPROVAL) {
      orchestrator.cancelPendingActions(orgId, incident.getId(), "approval rejected");
      incidents.save(incident);
    }
    audit.record(
        "REJECTED",
        "approval",
        approval.getId().toString(),
        approval.getIncidentId(),
        "PENDING",
        "REJECTED",
        audit.json(java.util.Map.of("actor", actor.userId().toString())),
        null,
        null);
    return approval;
  }

  private Approval getScoped(UUID orgId, UUID approvalId) {
    return approvals
        .findByOrgIdAndId(orgId, approvalId)
        .orElseThrow(() -> ApiException.notFound("Approval not found"));
  }
}
