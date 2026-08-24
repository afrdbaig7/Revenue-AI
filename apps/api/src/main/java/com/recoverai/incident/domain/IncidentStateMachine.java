package com.recoverai.incident.domain;

import com.recoverai.common.api.ApiException;
import com.recoverai.common.api.ErrorCode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validated recovery-incident state machine. Every transition writes an audit event
 * (enforced by the incident service); terminal states only move to CLOSED.
 *
 * <p>Concurrency: transitions are CAS-protected by the entity {@code version}; the
 * service re-reads on optimistic-lock failure and re-applies the transition from the
 * current state, so concurrent workers converge safely.
 */
public final class IncidentStateMachine {

  private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED = new EnumMap<>(IncidentStatus.class);

  static {
    allow(IncidentStatus.DETECTED, IncidentStatus.RECONCILING, IncidentStatus.CLOSED);
    allow(
        IncidentStatus.RECONCILING,
        IncidentStatus.DIAGNOSING,
        IncidentStatus.BLOCKED,
        IncidentStatus.CLOSED,
        IncidentStatus.OPTED_OUT);
    allow(IncidentStatus.DIAGNOSING, IncidentStatus.STRATEGY_SELECTED, IncidentStatus.BLOCKED, IncidentStatus.CLOSED);
    allow(
        IncidentStatus.STRATEGY_SELECTED,
        IncidentStatus.POLICY_EVALUATING,
        IncidentStatus.BLOCKED,
        IncidentStatus.CLOSED,
        IncidentStatus.AWAITING_APPROVAL);
    allow(
        IncidentStatus.POLICY_EVALUATING,
        IncidentStatus.SCHEDULED,
        IncidentStatus.AWAITING_APPROVAL,
        IncidentStatus.BLOCKED,
        IncidentStatus.OPTED_OUT,
        IncidentStatus.CLOSED);
    allow(
        IncidentStatus.AWAITING_APPROVAL,
        IncidentStatus.SCHEDULED,
        IncidentStatus.CANCELLED,
        IncidentStatus.EXPIRED,
        IncidentStatus.BLOCKED,
        IncidentStatus.CLOSED);
    allow(
        IncidentStatus.SCHEDULED,
        IncidentStatus.EXECUTING,
        IncidentStatus.LATE_AUTHORIZED,
        IncidentStatus.EXPIRED,
        IncidentStatus.CANCELLED,
        IncidentStatus.RECOVERED,
        IncidentStatus.RETRYABLE_FAILURE,
        IncidentStatus.CLOSED);
    allow(
        IncidentStatus.EXECUTING,
        IncidentStatus.RECOVERED,
        IncidentStatus.RETRYABLE_FAILURE,
        IncidentStatus.FAILED,
        IncidentStatus.ESCALATED,
        IncidentStatus.LATE_AUTHORIZED,
        IncidentStatus.CLOSED);
    allow(
        IncidentStatus.RETRYABLE_FAILURE,
        IncidentStatus.SCHEDULED,
        IncidentStatus.FAILED,
        IncidentStatus.EXPIRED,
        IncidentStatus.LATE_AUTHORIZED,
        IncidentStatus.ESCALATED,
        IncidentStatus.CLOSED);
    allow(IncidentStatus.BLOCKED, IncidentStatus.CLOSED);
    allow(IncidentStatus.ESCALATED, IncidentStatus.CLOSED, IncidentStatus.SCHEDULED, IncidentStatus.EXECUTING);
    allow(IncidentStatus.LATE_AUTHORIZED, IncidentStatus.CLOSED);
    allow(IncidentStatus.RECOVERED, IncidentStatus.CLOSED);
    allow(IncidentStatus.FAILED, IncidentStatus.CLOSED);
    allow(IncidentStatus.OPTED_OUT, IncidentStatus.CLOSED);
    allow(IncidentStatus.EXPIRED, IncidentStatus.CLOSED);
    allow(IncidentStatus.CANCELLED, IncidentStatus.CLOSED);
    allow(IncidentStatus.CLOSED);
  }

  private static void allow(IncidentStatus from, IncidentStatus... to) {
    if (to.length == 0) {
      ALLOWED.put(from, EnumSet.noneOf(IncidentStatus.class));
    } else {
      ALLOWED.put(from, EnumSet.copyOf(java.util.List.of(to)));
    }
  }

  public static IncidentStatus transition(IncidentStatus from, IncidentStatus to) {
    if (from == to) {
      return to;
    }
    Set<IncidentStatus> allowed = ALLOWED.getOrDefault(from, Set.of());
    if (!allowed.contains(to)) {
      throw new ApiException(
          ErrorCode.INCIDENT_STATE_INVALID,
          "Invalid incident transition " + from + " -> " + to,
          409);
    }
    return to;
  }
}
