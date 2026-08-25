package com.recoverai.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A scheduled/executed recovery action. {@code idempotencyKey} is
 * {@code incidentId:strategy:attemptNumber} — unique at the DB level, so duplicate
 * execution attempts are impossible.
 */
@Entity
@Table(name = "recovery_actions")
@Getter
@Setter
@NoArgsConstructor
public class RecoveryAction {

  public enum Status {
    SCHEDULED,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    BLOCKED,
    SKIPPED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(nullable = false, length = 40)
  private String strategy;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status = Status.SCHEDULED;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber = 1;

  @Column(name = "scheduled_for", nullable = false)
  private Instant scheduledFor;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 190)
  private String idempotencyKey;

  @Column(name = "provider_reference", length = 190)
  private String providerReference;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "provider_response", columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode providerResponse;

  @Column(length = 64)
  private String result;

  @Column(length = 500)
  private String error;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  private long version;

  public RecoveryAction(
      UUID orgId, UUID incidentId, String strategy, int attemptNumber, Instant scheduledFor, String idempotencyKey) {
    this.orgId = orgId;
    this.incidentId = incidentId;
    this.strategy = strategy;
    this.attemptNumber = attemptNumber;
    this.scheduledFor = scheduledFor;
    this.idempotencyKey = idempotencyKey;
  }

  public static String idempotencyKeyFor(UUID incidentId, String strategy, int attemptNumber) {
    return incidentId + ":" + strategy + ":" + attemptNumber;
  }
}
