package com.recoverai.merchant.domain;

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

/**
 * Provider credentials for a merchant. Secrets are encrypted at rest (AES-GCM) and
 * never exposed to the browser or logs.
 */
@Entity
@Table(name = "merchant_integrations")
@Getter
@Setter
@NoArgsConstructor
public class MerchantIntegration {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(nullable = false, length = 16)
  private String mode = "TEST";

  @Column(name = "key_id_encrypted")
  private String keyIdEncrypted;

  @Column(name = "key_secret_encrypted")
  private String keySecretEncrypted;

  @Column(name = "webhook_secret_encrypted", nullable = false)
  private String webhookSecretEncrypted;

  @Column(nullable = false, length = 24)
  private String status = "ACTIVE";

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public MerchantIntegration(UUID orgId, UUID merchantId, String provider, String mode) {
    this.orgId = orgId;
    this.merchantId = merchantId;
    this.provider = provider;
    this.mode = mode;
  }
}
