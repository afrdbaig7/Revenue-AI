package com.recoverai.communication.domain;

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

/** Outbound customer communication. Bodies are stored redacted; simulated sends flagged. */
@Entity
@Table(name = "communications")
@Getter
@Setter
@NoArgsConstructor
public class Communication {

  public enum Channel {
    EMAIL,
    SMS,
    WHATSAPP,
    DEMO_INBOX,
    PUSH
  }

  public enum Status {
    QUEUED,
    SENT,
    FAILED,
    BLOCKED,
    OPTED_OUT,
    SIMULATED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "incident_id")
  private UUID incidentId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Channel channel;

  @Column(length = 64)
  private String template;

  @Column(length = 255)
  private String subject;

  @Column(name = "body_redacted", nullable = false)
  private String bodyRedacted;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status = Status.QUEUED;

  @Column(nullable = false)
  private boolean simulated;

  @Column(name = "provider_message_id", length = 120)
  private String providerMessageId;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
}
