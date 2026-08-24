package com.recoverai;

import com.recoverai.common.config.RecoverAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RecoverAI — AI Revenue Recovery &amp; Payment Reliability Engine.
 *
 * <p>Architecture: modular monolith for transactional business logic; the AI decision
 * service and event workers scale independently. PostgreSQL is the system of record;
 * Kafka/Redpanda carries outboxed events; Temporal (or the DB-backed scheduler in demo
 * mode) owns durable timers. See docs/architecture.
 */
@SpringBootApplication(scanBasePackages = "com.recoverai")
@ComponentScan(
    basePackages = "com.recoverai",
    excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.recoverai\\.tools\\..*"))
@EnableScheduling
@EnableConfigurationProperties(RecoverAiProperties.class)
public class RecoverAiApplication {

  public static void main(String[] args) {
    SpringApplication.run(RecoverAiApplication.class, args);
  }
}
