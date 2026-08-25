package com.recoverai.recovery.application;

import com.recoverai.audit.application.AuditService;
import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.infrastructure.RecoveryActionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-backed scheduler (demo/dev fallback; Temporal is the production orchestrator).
 * Polls due recovery actions and executes them via {@link RecoveryOrchestrator}, then
 * reconciles stale incidents with the provider. All execution paths are idempotent.
 */
@Component
@Slf4j
public class RecoveryScheduler {

  private final RecoveryActionRepository actions;
  private final RevenueIncidentRepository incidents;
  private final RecoveryOrchestrator orchestrator;
  private final AuditService audit;
  private final RecoverAiProperties props;
  private final io.micrometer.core.instrument.Counter workflowFailures;

  public RecoveryScheduler(
      RecoveryActionRepository actions,
      RevenueIncidentRepository incidents,
      RecoveryOrchestrator orchestrator,
      AuditService audit,
      RecoverAiProperties props,
      io.micrometer.core.instrument.MeterRegistry registry) {
    this.actions = actions;
    this.incidents = incidents;
    this.orchestrator = orchestrator;
    this.audit = audit;
    this.props = props;
    this.workflowFailures =
        io.micrometer.core.instrument.Counter.builder("workflow_failure_total")
            .description("Recovery workflow/scheduler execution failures")
            .register(registry);
  }

  @Scheduled(fixedDelayString = "${recoverai.scheduling.action-poll-ms:5000}")
  public void executeDueActions() {
    List<RecoveryAction> due = actions.findDue(Instant.now(), PageRequest.of(0, 100));
    for (RecoveryAction action : due) {
      // Scheduler thread — no request context; tenant travels with the action row.
      com.recoverai.common.tenant.TenantContext.setOrgId(action.getOrgId());
      org.slf4j.MDC.put("tenantId", action.getOrgId().toString());
      try {
        orchestrator.executeAction(action.getOrgId(), action.getId());
      } catch (Exception e) {
        workflowFailures.increment();
        log.error("ACTION_EXECUTION_FAILED action={} error={}", action.getId(), e.getClass().getSimpleName() + ": " + e.getMessage(), e);
      } finally {
        com.recoverai.common.tenant.TenantContext.clear();
        org.slf4j.MDC.remove("tenantId");
      }
    }
  }

  /** Re-drive incidents that are stale in flight states (reconciles provider state). */
  @Scheduled(fixedDelayString = "${recoverai.scheduling.reconcile-poll-ms:30000}")
  @Transactional
  public void reconcileStaleIncidents() {
    List<RevenueIncident> stale = incidents.findByStatusIn(
        List.of(IncidentStatus.RECONCILING, IncidentStatus.EXECUTING, IncidentStatus.RETRYABLE_FAILURE),
        PageRequest.of(0, 200));
    for (RevenueIncident incident : stale) {
      // Scheduler threads carry no request context — tenant travels with the incident.
      com.recoverai.common.tenant.TenantContext.setOrgId(incident.getOrgId());
      org.slf4j.MDC.put("tenantId", incident.getOrgId().toString());
      try {
        orchestrator.runPipeline(incident.getOrgId(), incident.getId());
      } catch (Exception e) {
        log.warn("RECONCILE_DRIVE_FAILED incident={} error={}", incident.getId(), e.getMessage());
      } finally {
        com.recoverai.common.tenant.TenantContext.clear();
        org.slf4j.MDC.remove("tenantId");
      }
    }
  }
}
