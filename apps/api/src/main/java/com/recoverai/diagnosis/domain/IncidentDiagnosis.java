package com.recoverai.diagnosis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A diagnosis fact for an incident: what happened, how confident we are, on what evidence. */
@Entity
@Table(name = "incident_diagnoses")
@Getter
@Setter
@NoArgsConstructor
public class IncidentDiagnosis {

  public enum Layer {
    DETERMINISTIC,
    AI,
    HYBRID
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Layer layer;

  @Column(name = "failure_category", nullable = false, length = 40)
  private String failureCategory;

  @Column(nullable = false, precision = 5, scale = 4)
  private BigDecimal confidence;

  @Column(nullable = false, length = 32)
  private String source;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private java.util.List<String> evidence = new java.util.ArrayList<>();

  @Column(name = "recommended_action", length = 40)
  private String recommendedAction;

  @Column(name = "model_version", length = 64)
  private String modelVersion;

  @Column(name = "prompt_version", length = 64)
  private String promptVersion;

  @Column(name = "raw_input_redacted")
  private String rawInputRedacted;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
}
