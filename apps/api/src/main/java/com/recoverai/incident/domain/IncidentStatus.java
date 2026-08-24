package com.recoverai.incident.domain;

/** Recovery incident workflow states. See docs/architecture/recovery-state-machine.md. */
public enum IncidentStatus {
  DETECTED,
  RECONCILING,
  DIAGNOSING,
  STRATEGY_SELECTED,
  POLICY_EVALUATING,
  AWAITING_APPROVAL,
  SCHEDULED,
  EXECUTING,
  RECOVERED,
  RETRYABLE_FAILURE,
  FAILED,
  ESCALATED,
  BLOCKED,
  OPTED_OUT,
  EXPIRED,
  CANCELLED,
  LATE_AUTHORIZED,
  CLOSED;

  public boolean isTerminal() {
    return switch (this) {
      case RECOVERED, FAILED, BLOCKED, OPTED_OUT, EXPIRED, CANCELLED, LATE_AUTHORIZED, CLOSED -> true;
      default -> false;
    };
  }
}
