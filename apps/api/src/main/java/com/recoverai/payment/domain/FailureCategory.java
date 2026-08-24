package com.recoverai.payment.domain;

/** Normalized failure taxonomy. Original provider codes are preserved separately. */
public enum FailureCategory {
  INSUFFICIENT_FUNDS,
  CARD_EXPIRED,
  CARD_BLOCKED,
  BANK_DECLINE,
  MANDATE_CANCELLED,
  MANDATE_FAILURE,
  NETWORK_TIMEOUT,
  PROCESSOR_ERROR,
  AUTHENTICATION_FAILURE,
  CUSTOMER_ABORTED,
  CHECKOUT_ABANDONED,
  UNKNOWN;

  /** Categories that usually resolve with time — retry later is rational. */
  public boolean isTransient() {
    return this == INSUFFICIENT_FUNDS || this == NETWORK_TIMEOUT || this == PROCESSOR_ERROR;
  }

  /** Categories that require customer action (new card, new method, link). */
  public boolean requiresCustomerAction() {
    return this == CARD_EXPIRED
        || this == CARD_BLOCKED
        || this == MANDATE_CANCELLED
        || this == MANDATE_FAILURE
        || this == AUTHENTICATION_FAILURE
        || this == CHECKOUT_ABANDONED;
  }
}
