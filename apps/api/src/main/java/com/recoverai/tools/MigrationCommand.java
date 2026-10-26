package com.recoverai.tools;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Flyway runs during startup; this command then closes the application cleanly. */
@Configuration
public class MigrationCommand {
  @Bean
  @ConditionalOnProperty(prefix = "recoverai.cli", name = "migrate", havingValue = "true")
  public ApplicationRunner migrationRunner(ConfigurableApplicationContext context) {
    return args -> SpringApplication.exit(context);
  }
}
