package com.recoverai.workflow.domain;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable recovery-execution workflow (Temporal).
 *
 * <p>Waits until the action's scheduled time (survives process restarts), then invokes
 * the recovery activity, which re-validates payment state, policy, window, and
 * opt-out before executing anything — the exact same idempotent path as the DB
 * scheduler. The {@code cancel} signal aborts a pending run (e.g. late authorization).
 */
@WorkflowInterface
public interface RecoveryWorkflow {

  /** Input carried in the workflow args (Jackson-serializable). */
  record Input(UUID orgId, UUID actionId, Instant scheduledFor, String strategy, int attemptNumber) {}

  @WorkflowMethod
  void runRecovery(Input input);

  @SignalMethod
  void cancel(String reason);

  @QueryMethod
  boolean isCancelled();
}
