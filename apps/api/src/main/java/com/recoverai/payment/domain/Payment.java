package com.recoverai.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Normalized payment record. Provider specifics live in provider_* columns + JSONB. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(name = "provider_payment_id", length = 64)
  private String providerPaymentId;

  @Column(name = "provider_order_id", length = 64)
  private String providerOrderId;

  @Column(name = "provider_account_reference", length = 64)
  private String providerAccountReference;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PaymentStatus status = PaymentStatus.CREATED;

  @Column(name = "payment_method", length = 32)
  private String paymentMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_category", length = 40)
  private FailureCategory failureCategory;

  @Column(name = "failure_code", length = 64)
  private String failureCode;

  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "provider_failure_details", columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode providerFailureDetails;

  @Column(length = 255)
  private String description;

  @Column(name = "captured_at")
  private Instant capturedAt;

  @Column(name = "failed_at")
  private Instant failedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  private long version;
}
