package com.recoverai.promise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A customer's explicit promise to pay: durable follow-up workflow state. */
@Entity
@Table(name = "promises_to_pay")
@Getter
@Setter
@NoArgsConstructor
public class PromiseToPay {

  public enum Status {
    PROMISED,
    SCHEDULED,
    DUE,
    FULFILLED,
    MISSED,
    CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "promised_amount_minor", nullable = false)
  private long promisedAmountMinor;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "promised_at", nullable = false)
  private Instant promisedAt;

  @Column(name = "preferred_time", length = 32)
  private String preferredTime;

  @Column(name = "preferred_channel", length = 16)
  private String preferredChannel;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status = Status.PROMISED;

  @Column(precision = 5, scale = 4)
  private BigDecimal confidence;

  @Column(length = 32)
  private String source;

  @Column(name = "fulfilled_at")
  private Instant fulfilledAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  private long version;
}
