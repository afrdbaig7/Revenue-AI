package com.recoverai.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recoverai.common.api.ApiException;
import com.recoverai.incident.domain.IncidentStateMachine;
import com.recoverai.incident.domain.IncidentStatus;
import org.junit.jupiter.api.Test;

/** Incident state machine: validated transitions only; terminal states are sticky. */
class IncidentStateMachineTest {

  @Test
  void happyPathTransitions() {
    assertThat(IncidentStateMachine.transition(IncidentStatus.DETECTED, IncidentStatus.RECONCILING))
        .isEqualTo(IncidentStatus.RECONCILING);
    assertThat(IncidentStateMachine.transition(IncidentStatus.RECONCILING, IncidentStatus.DIAGNOSING))
        .isEqualTo(IncidentStatus.DIAGNOSING);
    assertThat(IncidentStateMachine.transition(IncidentStatus.DIAGNOSING, IncidentStatus.STRATEGY_SELECTED))
        .isEqualTo(IncidentStatus.STRATEGY_SELECTED);
    assertThat(IncidentStateMachine.transition(IncidentStatus.STRATEGY_SELECTED, IncidentStatus.POLICY_EVALUATING))
        .isEqualTo(IncidentStatus.POLICY_EVALUATING);
    assertThat(IncidentStateMachine.transition(IncidentStatus.POLICY_EVALUATING, IncidentStatus.SCHEDULED))
        .isEqualTo(IncidentStatus.SCHEDULED);
    assertThat(IncidentStateMachine.transition(IncidentStatus.SCHEDULED, IncidentStatus.EXECUTING))
        .isEqualTo(IncidentStatus.EXECUTING);
    assertThat(IncidentStateMachine.transition(IncidentStatus.EXECUTING, IncidentStatus.RECOVERED))
        .isEqualTo(IncidentStatus.RECOVERED);
  }

  @Test
  void lateAuthorizationIsReachableFromScheduled() {
    assertThat(IncidentStateMachine.transition(IncidentStatus.SCHEDULED, IncidentStatus.LATE_AUTHORIZED))
        .isEqualTo(IncidentStatus.LATE_AUTHORIZED);
  }

  @Test
  void invalidTransitionRejected() {
    assertThatThrownBy(() ->
            IncidentStateMachine.transition(IncidentStatus.RECOVERED, IncidentStatus.SCHEDULED))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void terminalStatesCannotLeave() {
    for (IncidentStatus terminal : IncidentStatus.values()) {
      if (terminal.isTerminal()) {
        assertThatThrownBy(() -> IncidentStateMachine.transition(terminal, IncidentStatus.SCHEDULED))
            .isInstanceOf(ApiException.class);
      }
    }
  }

  @Test
  void selfTransitionIsIdentity() {
    assertThat(IncidentStateMachine.transition(IncidentStatus.SCHEDULED, IncidentStatus.SCHEDULED))
        .isEqualTo(IncidentStatus.SCHEDULED);
  }

  @Test
  void approvalFlowRejectsOnReject() {
    assertThat(IncidentStateMachine.transition(IncidentStatus.AWAITING_APPROVAL, IncidentStatus.CANCELLED))
        .isEqualTo(IncidentStatus.CANCELLED);
  }

  @Test
  void blockedCloses() {
    assertThat(IncidentStateMachine.transition(IncidentStatus.BLOCKED, IncidentStatus.CLOSED))
        .isEqualTo(IncidentStatus.CLOSED);
  }
}
