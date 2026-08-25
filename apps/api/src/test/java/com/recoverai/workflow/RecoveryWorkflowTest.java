package com.recoverai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;

import com.recoverai.workflow.application.RecoveryActivities;
import com.recoverai.workflow.domain.RecoveryWorkflow;
import com.recoverai.workflow.domain.RecoveryWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Temporal workflow tests using the in-memory {@link TestWorkflowEnvironment} — no
 * server needed. Verifies the durable-timer semantics: execution at the scheduled time,
 * and cancellation BEFORE execution (late authorization) prevents the activity.
 */
class RecoveryWorkflowTest {

  /** Temporal needs a real implementation class (mocks carry annotations wrongly). */
  static class TestActivities implements RecoveryActivities {
    final RecoveryActivities delegate = mock(RecoveryActivities.class);

    @Override
    public void executeRecoveryAction(UUID orgId, UUID actionId) {
      delegate.executeRecoveryAction(orgId, actionId);
    }

    @Override
    public void processDuePromise(UUID orgId, UUID promiseId) {
      delegate.processDuePromise(orgId, promiseId);
    }
  }

  private TestWorkflowEnvironment env;
  private TestActivities activities;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    activities = new TestActivities();
    Worker worker = env.newWorker("recovery-task-queue");
    worker.registerWorkflowImplementationTypes(RecoveryWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities);
    env.start();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  @Test
  void executesActivityAtScheduledTime() {
    UUID orgId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    RecoveryWorkflow workflow =
        client.newWorkflowStub(
            RecoveryWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("recovery-task-queue").setWorkflowId("wf-1").build());

    WorkflowClient.start(
        workflow::runRecovery,
        new RecoveryWorkflow.Input(orgId, actionId, Instant.now().plusSeconds(2), "PAYMENT_LINK", 1));

    // Advance the test clock past the scheduled time.
    env.sleep(Duration.ofSeconds(3));
    verify(activities.delegate, timeout(2000)).executeRecoveryAction(orgId, actionId);
  }

  @Test
  void cancelBeforeExecutionSkipsActivity() {
    UUID orgId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    RecoveryWorkflow workflow =
        client.newWorkflowStub(
            RecoveryWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("recovery-task-queue").setWorkflowId("wf-2").build());

    WorkflowClient.start(
        workflow::runRecovery,
        new RecoveryWorkflow.Input(orgId, actionId, Instant.now().plusSeconds(10), "DELAYED_RETRY", 1));

    workflow.cancel("late authorization");
    env.sleep(Duration.ofSeconds(3));
    verify(activities.delegate, never()).executeRecoveryAction(orgId, actionId);
    assertThat(workflow.isCancelled()).isTrue();
  }

  @Test
  void alreadyDueInputExecutesImmediately() {
    UUID orgId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    RecoveryWorkflow workflow =
        client.newWorkflowStub(
            RecoveryWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("recovery-task-queue").setWorkflowId("wf-3").build());

    WorkflowClient.start(
        workflow::runRecovery,
        new RecoveryWorkflow.Input(orgId, actionId, Instant.now().minusSeconds(5), "PAYMENT_LINK", 1));
    env.sleep(Duration.ofSeconds(1));
    verify(activities.delegate, timeout(2000)).executeRecoveryAction(orgId, actionId);
  }
}
