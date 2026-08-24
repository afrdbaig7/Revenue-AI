package com.recoverai.common.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * System-level metrics: DB pool usage gauges and failure counters that are not provided
 * by auto-configuration. (Kafka consumer lag requires broker-side metrics — e.g.
 * kafka-exporter / Redpanda metrics — and is documented in docs/architecture/scaling.md.)
 */
@Configuration
public class SystemMetricsConfig {

  @Bean
  public Gauge databasePoolUsageGauge(HikariDataSource dataSource, MeterRegistry registry) {
    com.zaxxer.hikari.HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
    return Gauge.builder("database_pool_usage", pool, p -> p.getActiveConnections())
        .description("Active connections in the Hikari pool")
        .tag("pool", "recoverai-pool")
        .register(registry);
  }

  @Bean
  public Gauge databasePoolTotalGauge(HikariDataSource dataSource, MeterRegistry registry) {
    return Gauge.builder("database_pool_total", dataSource, d -> d.getMaximumPoolSize())
        .description("Maximum pool size")
        .tag("pool", "recoverai-pool")
        .register(registry);
  }
}
