package com.recoverai.policy.domain;

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
 * Merchant-configurable bounded controls. The policy engine evaluates every recovery
 * decision against the active policy set; the AI cannot override these limits.
 */
@Entity
@Table(name = "policy_sets")
@Getter
@Setter
@NoArgsConstructor
public class PolicySet {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "merchant_id")
  private UUID merchantId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "max_retries", nullable = false)
  private int maxRetries = 3;

  @Column(name = "max_contact_attempts", nullable = false)
  private int maxContactAttempts = 2;

  @Column(name = "max_discount_percent", nullable = false)
  private int maxDiscountPercent = 10;

  @Column(name = "recovery_window_hours", nullable = false)
  private int recoveryWindowHours = 72;

  @Column(name = "minimum_recoverable_amount", nullable = false)
  private long minimumRecoverableAmount = 10_000;

  @Column(name = "contact_cooldown_hours", nullable = false)
  private int contactCooldownHours = 12;

  @Column(name = "require_approval_above_amount", nullable = false)
  private long requireApprovalAboveAmount = 1_000_000;

  @Column(name = "allow_whatsapp", nullable = false)
  private boolean allowWhatsApp = true;

  @Column(name = "allow_email", nullable = false)
  private boolean allowEmail = true;

  @Column(name = "allow_sms", nullable = false)
  private boolean allowSms = true;

  @Column(name = "allow_discounts", nullable = false)
  private boolean allowDiscounts = true;

  @Column(name = "allow_payment_links", nullable = false)
  private boolean allowPaymentLinks = true;

  @Column(name = "allow_delayed_retry", nullable = false)
  private boolean allowDelayedRetry = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  private long version;

  public PolicySet(UUID orgId, String name) {
    this.orgId = orgId;
    this.name = name;
  }
}
