package com.recoverai.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Pre-aggregated analytics snapshot — keeps dashboards off hot operational tables. */
@Entity
@Table(name = "metric_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class MetricSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "period_start", nullable = false)
  private Instant periodStart;

  @Column(name = "period_end", nullable = false)
  private Instant periodEnd;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode metrics;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public MetricSnapshot(UUID orgId, Instant periodStart, Instant periodEnd, com.fasterxml.jackson.databind.JsonNode metrics) {
    this.orgId = orgId;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.metrics = metrics;
  }
}
