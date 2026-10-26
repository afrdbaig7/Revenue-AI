package com.recoverai.workflow.application;

import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.promise.domain.PromiseToPay;
import com.recoverai.workflow.domain.PromiseWorkflow;
import com.recoverai.workflow.domain.RecoveryWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Temporal-backed launcher — starts durable workflows for recovery actions and promises. */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recoverai.temporal", name = "enabled", havingValue = "true")
public class TemporalWorkflowLauncher implements WorkflowLauncher {

  public static final String TASK_QUEUE = "recovery-task-queue";

  private final WorkflowClient workflowClient;
  private final com.recoverai.outbox.application.OutboxService outbox;
  private final com.fasterxml.jackson.databind.ObjectMapper mapper;
  private final com.recoverai.recovery.infrastructure.RecoveryActionRepository actions;
  private final com.recoverai.promise.infrastructure.PromiseToPayRepository promises;

  @Override
  public void launchRecovery(RecoveryAction action) {
    outbox.enqueue(action.getOrgId(), "recovery_action", action.getId().toString(), "workflow-events:recovery.start",
        mapper.createObjectNode().put("actionId", action.getId().toString()));
  }

  public void startRecovery(UUID orgId, UUID actionId) {
    RecoveryAction action = actions.findById(actionId).filter(a -> a.getOrgId().equals(orgId)).orElseThrow();
    if (action.getStatus() != RecoveryAction.Status.SCHEDULED) return;
    RecoveryWorkflow.Input input =
        new RecoveryWorkflow.Input(
            action.getOrgId(), action.getId(), action.getScheduledFor(), action.getStrategy(), action.getAttemptNumber());
    WorkflowOptions options =
        WorkflowOptions.newBuilder()
            .setWorkflowId("recovery-" + action.getId())
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowExecutionTimeout(Duration.ofHours(72))
            .build();
    try { WorkflowClient.start(workflowClient.newWorkflowStub(RecoveryWorkflow.class, options)::runRecovery, input); }
    catch (io.temporal.client.WorkflowExecutionAlreadyStarted ignored) { /* Stable workflow identity absorbs acceptance/commit races. */ }
    log.info("TEMPORAL_WORKFLOW_STARTED type=recovery workflowId=recovery-{} action={}", action.getId(), action.getId());
  }

  @Override
  public void launchPromise(PromiseToPay promise) {
    outbox.enqueue(promise.getOrgId(), "promise", promise.getId().toString(), "workflow-events:promise.start",
        mapper.createObjectNode().put("promiseId", promise.getId().toString()));
  }

  public void startPromise(UUID orgId, UUID promiseId) {
    PromiseToPay promise = promises.findByOrgIdAndId(orgId, promiseId).orElseThrow();
    PromiseWorkflow.Input input =
        new PromiseWorkflow.Input(promise.getOrgId(), promise.getId(), promise.getPromisedAt());
    WorkflowOptions options =
        WorkflowOptions.newBuilder()
            .setWorkflowId("promise-" + promise.getId())
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowExecutionTimeout(Duration.ofDays(30))
            .build();
    try { WorkflowClient.start(workflowClient.newWorkflowStub(PromiseWorkflow.class, options)::runPromise, input); }
    catch (io.temporal.client.WorkflowExecutionAlreadyStarted ignored) { /* Already durably accepted. */ }
    log.info("TEMPORAL_WORKFLOW_STARTED type=promise workflowId=promise-{} promise={}", promise.getId(), promise.getId());
  }
}
