package com.recoverai.workflow.infrastructure;

import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.workflow.application.RecoveryActivities;
import com.recoverai.workflow.application.TemporalWorkflowLauncher;
import com.recoverai.workflow.domain.PromiseWorkflowImpl;
import com.recoverai.workflow.domain.RecoveryWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal wiring — active only when {@code recoverai.temporal.enabled=true}.
 *
 * <p>Creates the service client, the workflow client, registers workflow + activity
 * implementations on the recovery task queue, and starts the worker in the background.
 * Workflow state is fully durable: timers survive process restarts (ADR-004).
 */
@Configuration
@ConditionalOnProperty(prefix = "recoverai.temporal", name = "enabled", havingValue = "true")
public class TemporalConfig {

  @Bean
  public WorkflowServiceStubs workflowServiceStubs(RecoverAiProperties props) {
    return WorkflowServiceStubs.newInstance(
        WorkflowServiceStubsOptions.newBuilder().setTarget(props.temporal().target()).build());
  }

  @Bean
  public WorkflowClient workflowClient(WorkflowServiceStubs stubs, RecoverAiProperties props) {
    return WorkflowClient.newInstance(
        stubs,
        WorkflowClientOptions.newBuilder().setNamespace(props.temporal().namespace()).build());
  }

  @Bean
  public WorkerFactory workerFactory(WorkflowClient client, RecoveryActivities activities) {
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(TemporalWorkflowLauncher.TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(RecoveryWorkflowImpl.class, PromiseWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities);
    return factory;
  }

  @Bean
  public ApplicationRunner temporalWorkerRunner(WorkerFactory factory) {
    return args -> {
      factory.start();
      org.slf4j.LoggerFactory.getLogger(TemporalConfig.class)
          .info("TEMPORAL_WORKER_STARTED taskQueue={}", TemporalWorkflowLauncher.TASK_QUEUE);
    };
  }
}
