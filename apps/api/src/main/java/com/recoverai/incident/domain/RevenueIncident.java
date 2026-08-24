package com.recoverai.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The core workflow entity: one unit of revenue at risk and its recovery journey. */
@Entity
@Table(name = "revenue_incidents")
@Getter
@Setter
@NoArgsConstructor
public class RevenueIncident {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "payment_id")
  private UUID paymentId;

  @Column(name = "subscription_id")
  private UUID subscriptionId;

  @Column(name = "checkout_session_id")
  private UUID checkoutSessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "incident_type", nullable = false, length = 32)
  private IncidentType incidentType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private IncidentStatus status = IncidentStatus.DETECTED;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "failure_category", length = 40)
  private String failureCategory;

  @Column(name = "diagnosis_confidence", precision = 5, scale = 4)
  private BigDecimal diagnosisConfidence;

  @Column(name = "diagnosis_layer", length = 16)
  private String diagnosisLayer;

  @Column(name = "selected_strategy", length = 40)
  private String selectedStrategy;

  @Column(name = "attempts_count", nullable = false)
  private int attemptsCount;

  @Column(name = "contact_count", nullable = false)
  private int contactCount;

  @Column(name = "recovered_amount_minor", nullable = false)
  private long recoveredAmountMinor;

  @Column(name = "intervention_cost_minor", nullable = false)
  private long interventionCostMinor;

  @Column(name = "net_recovered_minor", nullable = false)
  private long netRecoveredMinor;

  @Column(name = "recovery_window_ends_at")
  private Instant recoveryWindowEndsAt;

  @Column(name = "next_action_at")
  private Instant nextActionAt;

  @Column(name = "detected_at", nullable = false, updatable = false)
  private Instant detectedAt = Instant.now();

  @Column(name = "diagnosed_at")
  private Instant diagnosedAt;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "recovered_at")
  private Instant recoveredAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "cancellation_reason", length = 255)
  private String cancellationReason;

  @Column(name = "policy_result", length = 32)
  private String policyResult;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "evidence_summary", columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode evidenceSummary;

  @Column(name = "experiment_arm", length = 16)
  private String experimentArm;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  private long version;

  public boolean isOpen() {
    return !status.isTerminal();
  }
}
