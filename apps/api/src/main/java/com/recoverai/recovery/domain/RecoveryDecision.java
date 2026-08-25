package com.recoverai.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * One decision record: which candidates were considered, with scores, and which strategy
 * was chosen and why (model/prompt versions when AI ranked). Fully auditable.
 */
@Entity
@Table(name = "recovery_decisions")
@Getter
@Setter
@NoArgsConstructor
public class RecoveryDecision {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private java.util.List<CandidateView> candidates = new java.util.ArrayList<>();

  @Column(name = "chosen_strategy", nullable = false, length = 40)
  private String chosenStrategy;

  @Column(length = 500)
  private String reason;

  @Column(precision = 5, scale = 4)
  private BigDecimal confidence;

  @Column(name = "ranking_source", nullable = false, length = 16)
  private String rankingSource = "DETERMINISTIC";

  @Column(name = "model_version", length = 64)
  private String modelVersion;

  @Column(name = "prompt_version", length = 64)
  private String promptVersion;

  @Column(name = "policy_result", length = 32)
  private String policyResult;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Serializable candidate snapshot (EV math inputs + outputs). */
  public record CandidateView(
      String strategy,
      double probability,
      long expectedValueMinor,
      long expectedGrossMinor,
      long interventionCostMinor,
      long discountCostMinor,
      long riskPenaltyMinor,
      long frictionPenaltyMinor,
      int timeToRecoveryHours,
      String rationale) {}
}
