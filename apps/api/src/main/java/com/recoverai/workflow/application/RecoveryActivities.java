package com.recoverai.workflow.application;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.UUID;

/**
 * Activities invoked by Temporal workflows. These call the SAME idempotent platform
 * services as the DB scheduler — re-validating payment state, policy, window, and
 * opt-out before any side effect.
 */
@ActivityInterface
public interface RecoveryActivities {

  @ActivityMethod
  void executeRecoveryAction(UUID orgId, UUID actionId);

  @ActivityMethod
  void processDuePromise(UUID orgId, UUID promiseId);
}
