package com.recoverai.workflow.application;

import com.recoverai.common.api.ApiException;
import com.recoverai.promise.domain.PromiseToPay;
import com.recoverai.promise.infrastructure.PromiseToPayRepository;
import com.recoverai.recovery.application.RecoveryOrchestrator;
import com.recoverai.recovery.infrastructure.RecoveryActionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Activity implementations — thin, idempotent wrappers over the deterministic services. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecoveryActivitiesImpl implements RecoveryActivities {

  private final RecoveryOrchestrator orchestrator;
  private final RecoveryActionRepository actions;
  private final PromiseToPayRepository promises;
  private final com.recoverai.promise.application.PromiseService promiseService;

  @Override
  public void executeRecoveryAction(UUID orgId, UUID actionId) {
    com.recoverai.common.tenant.TenantContext.setOrgId(orgId);
    try {
    actions.findById(actionId).ifPresentOrElse(
        action -> {
          try {
            orchestrator.executeAction(orgId, actionId);
          } catch (Exception e) {
            log.warn("TEMPORAL_ACTIVITY_ACTION_FAILED action={} error={}", actionId, e.getMessage());
            throw new RuntimeException(e); // bounded retry by Temporal, then workflow failure
          }
        },
        () -> {
          throw ApiException.notFound("Action not found");
        });
    } finally { com.recoverai.common.tenant.TenantContext.clear(); }
  }

  @Override
  public void processDuePromise(UUID orgId, UUID promiseId) {
    promises.findById(promiseId).ifPresentOrElse(
        promise -> {
          try {
            if (!promise.getOrgId().equals(orgId)) throw ApiException.notFound("Promise not found");
            promiseService.processPromise(orgId, promiseId);
          } catch (Exception e) {
            log.warn("TEMPORAL_ACTIVITY_PROMISE_FAILED promise={} error={}", promiseId, e.getMessage());
            throw new RuntimeException(e);
          }
        },
        () -> {
          throw ApiException.notFound("Promise not found");
        });
  }
}
