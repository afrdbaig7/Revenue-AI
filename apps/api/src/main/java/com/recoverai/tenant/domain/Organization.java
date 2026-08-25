package com.recoverai.tenant.domain;

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

/** SaaS tenant. Every merchant-owned row is scoped by org_id. */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, unique = true, length = 64)
  private String slug;

  @Column(nullable = false, length = 32)
  private String plan = "FREE";

  @Column(nullable = false, length = 24)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Organization(String name, String slug) {
    this.name = name;
    this.slug = slug;
  }
}
