package com.recoverai.workflow.domain;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Instant;
import java.util.UUID;

/** Durable promise-to-pay follow-up: a timer until the promised time, then a reminder activity. */
@WorkflowInterface
public interface PromiseWorkflow {

  record Input(UUID orgId, UUID promiseId, Instant promisedAt) {}

  @WorkflowMethod
  void runPromise(Input input);
}
