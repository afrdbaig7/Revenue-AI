package com.recoverai.workflow.application;

import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.promise.domain.PromiseToPay;
import java.util.UUID;

/**
 * Launches durable workflows for scheduled work. The Temporal implementation starts a
 * real workflow; the no-op implementation is used when Temporal is disabled (demo/dev),
 * where the DB scheduler owns execution. Both paths converge on the same idempotent
 * activities, so running both simultaneously is also safe.
 */
public interface WorkflowLauncher {

  void launchRecovery(RecoveryAction action);

  void launchPromise(PromiseToPay promise);

  /** No-op launcher for non-Temporal deployments. */
  class Noop implements WorkflowLauncher {
    @Override
    public void launchRecovery(RecoveryAction action) {}

    @Override
    public void launchPromise(PromiseToPay promise) {}
  }

  /** Record kept small: workflow needs org + action id + schedule time. */
  record LaunchResult(UUID workflowId) {}
}
