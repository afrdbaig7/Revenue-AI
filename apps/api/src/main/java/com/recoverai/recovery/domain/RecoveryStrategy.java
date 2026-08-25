package com.recoverai.recovery.domain;

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

/** Strategy catalog — first-class entities, seeded by Flyway. */
@Entity
@Table(name = "recovery_strategies")
@Getter
@Setter
@NoArgsConstructor
public class RecoveryStrategy {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 40)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "requires_adapter", length = 40)
  private String requiresAdapter;

  @Column(name = "requires_approval", nullable = false)
  private boolean requiresApproval;

  @Column(name = "cost_minor_base", nullable = false)
  private long costMinorBase;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
}
