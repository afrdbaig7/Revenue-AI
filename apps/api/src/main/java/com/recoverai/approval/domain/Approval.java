package com.recoverai.approval.domain;

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

/** Human-in-the-loop approval request for a recovery proposal. */
@Entity
@Table(name = "approvals")
@Getter
@Setter
@NoArgsConstructor
public class Approval {

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "requested_by")
  private UUID requestedBy;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private com.fasterxml.jackson.databind.JsonNode proposal;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status = Status.PENDING;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_note", length = 500)
  private String decisionNote;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Approval(UUID orgId, UUID incidentId, com.fasterxml.jackson.databind.JsonNode proposal) {
    this.orgId = orgId;
    this.incidentId = incidentId;
    this.proposal = proposal;
  }
}
