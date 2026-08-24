package com.recoverai.payment.domain;

/** Normalized internal payment states (provider details retained separately). */
public enum PaymentStatus {
  CREATED,
  PENDING,
  AUTHORIZED,
  CAPTURED,
  FAILED,
  REFUNDED,
  PARTIALLY_REFUNDED;

  /** Terminal for collection purposes — recovery must never re-collect these. */
  public boolean isCollectable() {
    return this == CAPTURED || this == AUTHORIZED || this == REFUNDED || this == PARTIALLY_REFUNDED;
  }

  public boolean isFailedOrPending() {
    return this == FAILED || this == PENDING || this == CREATED;
  }
}
