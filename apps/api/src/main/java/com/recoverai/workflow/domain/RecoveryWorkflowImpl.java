package com.recoverai.workflow.domain;

import com.recoverai.workflow.application.RecoveryActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/** Temporal implementation of {@link RecoveryWorkflow}. Deterministic: no clocks, no IO. */
@Slf4j
public class RecoveryWorkflowImpl implements RecoveryWorkflow {

  private final RecoveryActivities activities =
      Workflow.newActivityStub(
          RecoveryActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofMinutes(2))
              .setRetryOptions(
                  io.temporal.common.RetryOptions.newBuilder()
                      .setMaximumAttempts(3)
                      .setInitialInterval(Duration.ofSeconds(1))
                      .setBackoffCoefficient(2.0)
                      .build())
              .build());

  private boolean cancelled = false;
  private String cancelReason;

  @Override
  public void runRecovery(Input input) {
    long now = Workflow.currentTimeMillis();
    long scheduled = input.scheduledFor().toEpochMilli();
    Duration wait = scheduled > now ? Duration.ofMillis(scheduled - now) : Duration.ZERO;

    try {
      Workflow.sleep(wait);
      if (!cancelled) {
        activities.executeRecoveryAction(input.orgId(), input.actionId());
      } else {
        log.info("TEMPORAL_WORKFLOW_SKIPPED action={} reason={}", input.actionId(), cancelReason);
      }
    } catch (CanceledFailure e) {
      // Workflow cancelled (e.g. late authorization) — the DB action row is also
      // cancelled by the platform; nothing further to do.
      log.info("TEMPORAL_WORKFLOW_CANCELLED action={}", input.actionId());
    }
  }

  @Override
  public void cancel(String reason) {
    this.cancelled = true;
    this.cancelReason = reason;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }
}
