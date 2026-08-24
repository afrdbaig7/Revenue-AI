package com.recoverai.outbox.domain;

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
 * Transactional outbox: business mutation + outbox row commit atomically, then a
 * publisher emits to Kafka (or inline dispatch in demo mode). See ADR-005.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "aggregate_type", nullable = false, length = 64)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 64)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode payload;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  @Column(nullable = false, length = 24)
  private String status = "PENDING";

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt = Instant.now();

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public OutboxEvent(
      UUID orgId, String aggregateType, String aggregateId, String eventType, com.fasterxml.jackson.databind.JsonNode payload) {
    this.orgId = orgId;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
  }
}
