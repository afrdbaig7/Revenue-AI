package com.recoverai.customer.domain;

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

/** Customer (payer). Communication preferences + opt-out state live here. */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(name = "customer_ref", length = 128)
  private String customerRef;

  @Column(length = 320)
  private String email;

  @Column(length = 32)
  private String phone;

  @Column(name = "full_name", length = 160)
  private String fullName;

  @Column(length = 32)
  private String segment;

  @Column(name = "opt_out_at")
  private Instant optOutAt;

  @Column(name = "opt_out_reason", length = 32)
  private String optOutReason;

  @Column(name = "preferred_channel", length = 16)
  private String preferredChannel;

  @Column(name = "preferred_time_window", length = 16)
  private String preferredTimeWindow;

  @Column(name = "contact_count", nullable = false)
  private int contactCount;

  @Column(name = "last_contacted_at")
  private Instant lastContactedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public boolean optedOut() {
    return optOutAt != null;
  }

  public void recordContact() {
    contactCount++;
    lastContactedAt = Instant.now();
  }
}
