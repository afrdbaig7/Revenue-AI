package com.recoverai.workflow.domain;

import com.recoverai.workflow.application.RecoveryActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/** Temporal implementation of {@link PromiseWorkflow}. */
@Slf4j
public class PromiseWorkflowImpl implements PromiseWorkflow {

  private final RecoveryActivities activities =
      Workflow.newActivityStub(
          RecoveryActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofMinutes(2))
              .setRetryOptions(
                  io.temporal.common.RetryOptions.newBuilder()
                      .setMaximumAttempts(3)
                      .build())
              .build());

  @Override
  public void runPromise(Input input) {
    long now = Workflow.currentTimeMillis();
    long promised = input.promisedAt().toEpochMilli();
    Duration wait = promised > now ? Duration.ofMillis(promised - now) : Duration.ZERO;
    try {
      Workflow.sleep(wait);
      activities.processDuePromise(input.orgId(), input.promiseId());
    } catch (CanceledFailure e) {
      log.info("TEMPORAL_PROMISE_CANCELLED promise={}", input.promiseId());
    }
  }
}
