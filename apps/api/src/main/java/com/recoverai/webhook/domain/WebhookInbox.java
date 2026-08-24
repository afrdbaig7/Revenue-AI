package com.recoverai.webhook.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Raw webhook ledger. Unique on (provider, provider_event_id): duplicates are expected
 * and absorbed. Payloads are stored redacted (no card data, no tokens).
 */
@Entity
@Table(name = "webhook_inbox")
@Getter
@Setter
@NoArgsConstructor
public class WebhookInbox {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id")
  private UUID orgId;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(name = "provider_event_id", nullable = false, length = 128)
  private String providerEventId;

  @Column(name = "event_type", nullable = false, length = 96)
  private String eventType;

  @Column(name = "payload_hash", nullable = false, length = 64)
  private String payloadHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_payload", columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode rawPayload;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt = Instant.now();

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "processing_status", nullable = false, length = 24)
  private String processingStatus = "RECEIVED";

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(length = 500)
  private String error;

  public WebhookInbox(
      UUID orgId,
      String provider,
      String providerEventId,
      String eventType,
      String payloadHash,
      com.fasterxml.jackson.databind.JsonNode rawPayload) {
    this.orgId = orgId;
    this.provider = provider;
    this.providerEventId = providerEventId;
    this.eventType = eventType;
    this.payloadHash = payloadHash;
    this.rawPayload = rawPayload;
  }
}
