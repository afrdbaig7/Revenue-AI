package com.recoverai.experiment.domain;

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

/**
 * A batch evaluation run: the SAME seeded incident population through CONTROL
 * (fixed baseline strategy) and TREATMENT (RecoverAI). Results are synthetic and
 * labeled as such — never presented as real-world causal evidence.
 */
@Entity
@Table(name = "experiments")
@Getter
@Setter
@NoArgsConstructor
public class Experiment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false)
  private long seed;

  @Column(name = "population_size", nullable = false)
  private int populationSize;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "baseline_config", nullable = false, columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode baselineConfig;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "treatment_config", nullable = false, columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode treatmentConfig;

  @Column(nullable = false, length = 24)
  private String status = "RUNNING";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode results;

  @Column(name = "report_format", length = 16)
  private String reportFormat;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "completed_at")
  private Instant completedAt;

  public Experiment(UUID orgId, String name, long seed, int populationSize) {
    this.orgId = orgId;
    this.name = name;
    this.seed = seed;
    this.populationSize = populationSize;
  }
}
