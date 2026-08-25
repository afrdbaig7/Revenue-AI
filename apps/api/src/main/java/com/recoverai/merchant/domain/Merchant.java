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

/** Merchant entity (owned by an organization). */
@Entity
@Table(name = "merchants")
@Getter
@Setter
@NoArgsConstructor
public class Merchant {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 24)
  private String status = "ACTIVE";

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(nullable = false, length = 2)
  private String country = "IN";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Merchant(UUID orgId, String name) {
    this.orgId = orgId;
    this.name = name;
  }
}
