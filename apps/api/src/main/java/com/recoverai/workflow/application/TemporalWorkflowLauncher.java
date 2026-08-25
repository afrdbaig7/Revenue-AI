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

  @Override
  public void launchRecovery(RecoveryAction action) {
    RecoveryWorkflow.Input input =
        new RecoveryWorkflow.Input(
            action.getOrgId(), action.getId(), action.getScheduledFor(), action.getStrategy(), action.getAttemptNumber());
    WorkflowOptions options =
        WorkflowOptions.newBuilder()
            .setWorkflowId("recovery-" + action.getId())
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowExecutionTimeout(Duration.ofHours(72))
            .build();
    WorkflowClient.start(workflowClient.newWorkflowStub(RecoveryWorkflow.class, options)::runRecovery, input);
    log.info("TEMPORAL_WORKFLOW_STARTED type=recovery workflowId=recovery-{} action={}", action.getId(), action.getId());
  }

  @Override
  public void launchPromise(PromiseToPay promise) {
    PromiseWorkflow.Input input =
        new PromiseWorkflow.Input(promise.getOrgId(), promise.getId(), promise.getPromisedAt());
    WorkflowOptions options =
        WorkflowOptions.newBuilder()
            .setWorkflowId("promise-" + promise.getId())
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowExecutionTimeout(Duration.ofDays(30))
            .build();
    WorkflowClient.start(workflowClient.newWorkflowStub(PromiseWorkflow.class, options)::runPromise, input);
    log.info("TEMPORAL_WORKFLOW_STARTED type=promise workflowId=promise-{} promise={}", promise.getId(), promise.getId());
  }
}
