package com.recoverai.recovery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.incident.application.IncidentService;
import com.recoverai.incident.domain.*;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.integration.domain.PaymentProvider;
import com.recoverai.payment.application.PaymentService;
import com.recoverai.payment.domain.*;
import com.recoverai.payment.infrastructure.PaymentRepository;
import com.recoverai.policy.application.PolicyEngine;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import com.recoverai.recovery.application.RecoveryOrchestrator;
import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.infrastructure.RecoveryActionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RecoveryExecutionTest {
  private final Map<Class<?>, Object> dependencies = new HashMap<>();
  private RecoveryOrchestrator orchestrator;
  private final UUID orgId = UUID.randomUUID(), incidentId = UUID.randomUUID(), actionId = UUID.randomUUID();
  private final AtomicBoolean transactionOpen = new AtomicBoolean();
  private RecoveryAction action;
  private RevenueIncident incident;
  private Payment payment;

  @SuppressWarnings("unchecked") private <T> T dependency(Class<T> type) { return (T) dependencies.get(type); }

  @BeforeEach void setup() throws Exception {
    dependencies.put(ObjectMapper.class, new ObjectMapper());
    dependencies.put(MeterRegistry.class, new SimpleMeterRegistry());
    dependencies.put(PolicyEngine.class, new PolicyEngine());
    var constructor = RecoveryOrchestrator.class.getConstructors()[0];
    Object[] args = Arrays.stream(constructor.getParameterTypes()).map(type -> dependencies.computeIfAbsent(type, key -> mock(key))).toArray();
    orchestrator = (RecoveryOrchestrator) constructor.newInstance(args);
    var manager = dependency(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenAnswer(call -> { transactionOpen.set(true); return new SimpleTransactionStatus(); });
    doAnswer(call -> { transactionOpen.set(false); return null; }).when(manager).commit(any());
    doAnswer(call -> { transactionOpen.set(false); return null; }).when(manager).rollback(any());
    action = new RecoveryAction(orgId, incidentId, "DELAYED_RETRY", 1, Instant.now().minusSeconds(1), "operation-1");
    action.setId(actionId);
    incident = new RevenueIncident();
    incident.setId(incidentId); incident.setOrgId(orgId); incident.setStatus(IncidentStatus.SCHEDULED);
    incident.setAmountMinor(1000); incident.setCurrency("INR"); incident.setPaymentId(UUID.randomUUID());
    incident.setRecoveryWindowEndsAt(Instant.now().plusSeconds(3600));
    payment = new Payment(); payment.setId(incident.getPaymentId()); payment.setOrgId(orgId);
    payment.setStatus(PaymentStatus.FAILED); payment.setAmountMinor(1000); payment.setCurrency("INR");
    when(dependency(RecoveryActionRepository.class).findById(actionId)).thenReturn(Optional.of(action));
    when(dependency(RecoveryActionRepository.class).findByIdForUpdate(actionId)).thenReturn(Optional.of(action));
    when(dependency(RevenueIncidentRepository.class).findByIdForUpdate(incidentId)).thenReturn(Optional.of(incident));
    when(dependency(IncidentService.class).get(orgId, incidentId)).thenReturn(incident);
    when(dependency(PaymentRepository.class).findByOrgIdAndId(orgId, payment.getId())).thenReturn(Optional.of(payment));
    when(dependency(PaymentService.class).reconcileFromProvider(orgId, payment)).thenReturn(new PaymentService.Reconciliation(payment, true, "VERIFIED"));
    var policy = new PolicySet(orgId, "test"); policy.setMaxRetries(3); policy.setMinimumRecoverableAmount(1); policy.setAllowDelayedRetry(true);
    when(dependency(PolicySetRepository.class).findByOrgIdAndActiveTrue(orgId)).thenReturn(Optional.of(policy));
  }

  @Test void providerCallsHappenAfterCommittedIntentAndReplayDoesNotExecuteAgain() throws Exception {
    when(dependency(PaymentProvider.class).createOrder(anyLong(), anyString(), anyString())).thenAnswer(call -> {
      assertThat(transactionOpen).isFalse();
      assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.EXECUTING);
      return new PaymentProvider.ProviderOrder("order-1", "receipt", 1000, "INR", "created", null);
    });
    orchestrator.executeAction(orgId, actionId);
    orchestrator.executeAction(orgId, actionId);
    assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.SUCCEEDED);
    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.AWAITING_PAYMENT);
    assertThat(incident.getAttemptsCount()).isEqualTo(1);
    verify(dependency(PaymentProvider.class), times(1)).createOrder(anyLong(), anyString(), anyString());
  }

  @Test void unavailableReconciliationDefersWithoutExternalEffects() {
    when(dependency(PaymentService.class).reconcileFromProvider(orgId, payment)).thenReturn(new PaymentService.Reconciliation(payment, false, "TRANSIENT"));
    orchestrator.executeAction(orgId, actionId);
    assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.SCHEDULED);
    assertThat(action.getScheduledFor()).isAfter(Instant.now());
    assertThat(incident.getAttemptsCount()).isZero();
    verifyNoInteractions(dependency(PaymentProvider.class));
  }

  @Test void transportFailureIsAmbiguousAndNotBlindlyRetried() throws Exception {
    when(dependency(PaymentProvider.class).createOrder(anyLong(), anyString(), anyString())).thenThrow(new PaymentProvider.ProviderException("TRANSIENT", "timeout"));
    orchestrator.executeAction(orgId, actionId);
    orchestrator.executeAction(orgId, actionId);
    assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.AMBIGUOUS);
    assertThat(incident.getAttemptsCount()).isEqualTo(1);
    verify(dependency(IncidentService.class)).transitionBestEffort(eq(orgId), eq(incidentId), eq(IncidentStatus.ESCALATED), anyString());
    verify(dependency(PaymentProvider.class), times(1)).createOrder(anyLong(), anyString(), anyString());
  }

  @Test void expiredExecutingIntentRequiresReconciliationAfterWorkerRestart() {
    action.setStatus(RecoveryAction.Status.EXECUTING); action.setLeaseUntil(Instant.now().minusSeconds(5));
    incident.setStatus(IncidentStatus.EXECUTING);
    orchestrator.recoverAbandonedAction(orgId, actionId);
    orchestrator.executeAction(orgId, actionId);
    assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.AMBIGUOUS);
    verifyNoInteractions(dependency(PaymentProvider.class));
  }
}
