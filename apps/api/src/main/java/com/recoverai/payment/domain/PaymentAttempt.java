package com.recoverai.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** One payment attempt (one provider attempt fact). */
@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;

  @Column(name = "attempt_no", nullable = false)
  private int attemptNo;

  @Column(name = "provider_attempt_id", length = 64)
  private String providerAttemptId;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(nullable = false, length = 32)
  private String result;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_category", length = 40)
  private FailureCategory failureCategory;

  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_details", columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode rawDetails;

  @Column(name = "attempted_at", nullable = false)
  private Instant attemptedAt = Instant.now();

  public PaymentAttempt(
      UUID orgId,
      UUID paymentId,
      int attemptNo,
      long amountMinor,
      String currency,
      String result) {
    this.orgId = orgId;
    this.paymentId = paymentId;
    this.attemptNo = attemptNo;
    this.amountMinor = amountMinor;
    this.currency = currency;
    this.result = result;
  }
}
