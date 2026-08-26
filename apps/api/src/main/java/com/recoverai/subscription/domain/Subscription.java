package com.recoverai.subscription.domain;

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

/**
 * Subscription lifecycle (normalized). RecoverAI does NOT assume it controls Razorpay's
 * own retry cycle — platform-managed retries are modeled as a state we wait on and
 * reconcile, per Razorpay's documented subscription behavior.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "provider_subscription_id", length = 64)
  private String providerSubscriptionId;

  @Column(name = "provider_plan_id", length = 64)
  private String providerPlanId;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(length = 24)
  private String cadence;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "current_period_start")
  private Instant currentPeriodStart;

  @Column(name = "current_period_end")
  private Instant currentPeriodEnd;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  private long version;
}
