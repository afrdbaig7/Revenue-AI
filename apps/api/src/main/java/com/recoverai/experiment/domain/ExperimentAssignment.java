package com.recoverai.experiment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One incident's outcome in one arm of an experiment. */
@Entity
@Table(name = "experiment_assignments")
@Getter
@Setter
@NoArgsConstructor
public class ExperimentAssignment {

  public enum Arm {
    CONTROL,
    TREATMENT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "experiment_id", nullable = false)
  private UUID experimentId;

  @Column(name = "incident_key", nullable = false, length = 64)
  private String incidentKey;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Arm arm;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "failure_category", length = 40)
  private String failureCategory;

  @Column(nullable = false)
  private boolean recovered;

  @Column(name = "recovered_amount_minor", nullable = false)
  private long recoveredAmountMinor;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private int contacts;

  @Column(name = "time_to_recovery_hours", precision = 8, scale = 2)
  private BigDecimal timeToRecoveryHours;

  @Column(name = "policy_blocks", nullable = false)
  private int policyBlocks;

  public ExperimentAssignment(UUID orgId, UUID experimentId, String incidentKey, Arm arm) {
    this.orgId = orgId;
    this.experimentId = experimentId;
    this.incidentKey = incidentKey;
    this.arm = arm;
  }
}
