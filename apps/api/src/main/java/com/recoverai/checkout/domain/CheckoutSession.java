package com.recoverai.checkout.domain;

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
 * Checkout session lifecycle: CART_CREATED → CHECKOUT_STARTED → PAYMENT_NOT_ATTEMPTED →
 * ABANDONED → RECOVERY_SCHEDULED → NUDGE_SENT → CHECKOUT_RESUMED → PAYMENT_COMPLETED.
 */
@Entity
@Table(name = "checkout_sessions")
@Getter
@Setter
@NoArgsConstructor
public class CheckoutSession {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "provider_session_id", length = 64)
  private String providerSessionId;

  @Column(nullable = false, length = 32)
  private String status = "CART_CREATED";

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "cart_ref", length = 64)
  private String cartRef;

  @Column(name = "abandoned_at")
  private Instant abandonedAt;

  @Column(name = "resumed_at")
  private Instant resumedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  private long version;
}
