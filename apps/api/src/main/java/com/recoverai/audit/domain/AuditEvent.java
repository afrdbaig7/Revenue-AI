package com.recoverai.audit.domain;

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
 * Immutable audit ledger. There is no update/delete API for these records; production DB
 * grants should additionally revoke UPDATE/DELETE from the application role.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "incident_id")
  private UUID incidentId;

  @Column(name = "entity_type", nullable = false, length = 64)
  private String entityType;

  @Column(name = "entity_id", length = 64)
  private String entityId;

  @Column(name = "actor_type", nullable = false, length = 24)
  private String actorType = "SYSTEM";

  @Column(name = "actor_id", length = 64)
  private String actorId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(nullable = false)
  private Instant timestamp = Instant.now();

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  @Column(name = "trace_id", length = 64)
  private String traceId;

  @Column(name = "previous_state", length = 40)
  private String previousState;

  @Column(name = "new_state", length = 40)
  private String newState;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "decision_input_snapshot", columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode decisionInputSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "decision_output_snapshot", columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode decisionOutputSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode metadata;

  public AuditEvent(UUID orgId, String eventType) {
    this.orgId = orgId;
    this.eventType = eventType;
  }
}
