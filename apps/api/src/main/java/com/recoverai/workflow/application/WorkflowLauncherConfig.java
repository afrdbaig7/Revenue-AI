package com.recoverai.workflow.application;

import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.promise.domain.PromiseToPay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the default no-op launcher when Temporal is disabled (demo/dev mode).
 * The DB scheduler remains the execution driver in that configuration.
 */
@Configuration
public class WorkflowLauncherConfig {

  @Bean
  @ConditionalOnMissingBean(WorkflowLauncher.class)
  public WorkflowLauncher noopWorkflowLauncher() {
    return new WorkflowLauncher.Noop();
  }
}
