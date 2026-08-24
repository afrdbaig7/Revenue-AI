package com.recoverai.incident.domain;

/** The kind of revenue risk an incident represents. */
public enum IncidentType {
  PAYMENT_FAILURE,
  SUBSCRIPTION_FAILURE,
  CHECKOUT_ABANDONMENT,
  PROMISE_TO_PAY
}
