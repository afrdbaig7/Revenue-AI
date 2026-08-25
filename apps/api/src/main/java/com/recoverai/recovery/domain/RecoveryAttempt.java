package com.recoverai.recovery.domain;

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

/** Execution outcome of one recovery attempt. */
@Entity
@Table(name = "recovery_attempts")
@Getter
@Setter
@NoArgsConstructor
public class RecoveryAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "action_id")
  private UUID actionId;

  @Column(name = "attempt_no", nullable = false)
  private int attemptNo;

  @Column(nullable = false, length = 40)
  private String strategy;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(length = 64)
  private String outcome;

  @Column(name = "recovered_amount_minor", nullable = false)
  private long recoveredAmountMinor;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  public RecoveryAttempt(
      UUID orgId, UUID incidentId, UUID actionId, int attemptNo, String strategy, String status) {
    this.orgId = orgId;
    this.incidentId = incidentId;
    this.actionId = actionId;
    this.attemptNo = attemptNo;
    this.strategy = strategy;
    this.status = status;
  }
}
