package com.recoverai.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentStatus;
import com.recoverai.policy.application.PolicyEngine;
import com.recoverai.policy.application.PolicyEngine.Evaluation;
import com.recoverai.policy.domain.PolicySet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Policy engine is the authorization gate — these rules are load-bearing. */
class PolicyEngineTest {

  @Test void delayedRetryHasItsOwnCapabilityGate() {
    setUp();
    policy.setAllowDelayedRetry(false);
    assertThat(engine.evaluate(policy, failedPayment, "DELAYED_RETRY", 0, 0, Instant.now(), 349_900).blockingRule()).isEqualTo("DELAYED_RETRY_PROHIBITED");
  }

  @Test void upiRecoveryCannotBypassPaymentLinkPolicy() {
    setUp();
    policy.setAllowPaymentLinks(false);
    assertThat(engine.evaluate(policy, failedPayment, "UPI_RECOVERY", 0, 0, Instant.now(), 349_900).blockingRule()).isEqualTo("PAYMENT_LINKS_PROHIBITED");
  }

  @Test void unsupportedStrategiesCannotBecomeSuccessfulNoops() {
    setUp();
    assertThat(engine.evaluate(policy, failedPayment, "PROMISE_TO_PAY", 0, 0, Instant.now(), 349_900).blockingRule()).isEqualTo("UNSUPPORTED_STRATEGY");
  }

  private PolicySet policy;
  private PolicyEngine engine;
  private Payment failedPayment;

  @BeforeEach
  void setUp() {
    policy = new PolicySet(UUID.randomUUID(), "test");
    policy.setMaxRetries(3);
    policy.setMaxContactAttempts(2);
    policy.setRecoveryWindowHours(72);
    policy.setMinimumRecoverableAmount(10_000);
    policy.setRequireApprovalAboveAmount(1_000_000);
    engine = new PolicyEngine();

    failedPayment = new Payment();
    failedPayment.setStatus(PaymentStatus.FAILED);
    failedPayment.setAmountMinor(349_900);
  }

  @Test
  void allowsRetryWithinLimits() {
    Evaluation result = engine.evaluate(policy, failedPayment, "DELAYED_RETRY", 1, 0, Instant.now(), 349_900);
    assertThat(result.allowed()).isTrue();
  }

  @Test
  void blocksWhenMaxRetriesReached() {
    Evaluation result = engine.evaluate(policy, failedPayment, "DELAYED_RETRY", 3, 0, Instant.now(), 349_900);
    assertThat(result.allowed()).isFalse();
    assertThat(result.blockingRule()).isEqualTo("MAX_RETRIES");
  }

  @Test
  void blocksWhenMaxContactsReached() {
    Evaluation result = engine.evaluate(policy, failedPayment, "WHATSAPP_NUDGE", 0, 2, Instant.now(), 349_900);
    assertThat(result.allowed()).isFalse();
    assertThat(result.blockingRule()).isEqualTo("MAX_CONTACTS");
  }

  @Test
  void blocksWhenPaymentAlreadyCollected() {
    // The hardest stopping rule: never re-collect money that is already safe.
    Payment captured = new Payment();
    captured.setStatus(PaymentStatus.CAPTURED);
    Evaluation result = engine.evaluate(policy, captured, "PAYMENT_LINK", 0, 0, Instant.now(), 349_900);
    assertThat(result.allowed()).isFalse();
    assertThat(result.blockingRule()).isEqualTo("PAYMENT_ALREADY_COLLECTED");
  }

  @Test
  void blocksWhenPaymentNotFailed() {
    Payment pending = new Payment();
    pending.setStatus(PaymentStatus.PENDING);
    Evaluation result = engine.evaluate(policy, pending, "DELAYED_RETRY", 0, 0, Instant.now(), 349_900);
    assertThat(result.allowed()).isFalse();
    assertThat(result.blockingRule()).isEqualTo("PAYMENT_NOT_FAILED");
  }

  @Test
  void blocksBelowMinimumAmount() {
    Evaluation result = engine.evaluate(policy, failedPayment, "DELAYED_RETRY", 0, 0, Instant.now(), 9_999);
    assertThat(result.allowed()).isFalse();
    assertThat(result.blockingRule()).isEqualTo("MINIMUM_AMOUNT");
  }

  @Test
  void blocksChannelsProhibitedByPolicy() {
    policy.setAllowEmail(false);
    Evaluation result = engine.evaluate(policy, failedPayment, "EMAIL_NUDGE", 0, 0, Instant.now(), 349_900);
    assertThat(result.allowed()).isFalse();
    assertThat(result.blockingRule()).isEqualTo("EMAIL_PROHIBITED");
  }

  @Test
  void requiresApprovalForHighValue() {
    assertThat(engine.requiresApproval(policy, 5_000_000, "PAYMENT_LINK", false)).isTrue();
  }

  @Test
  void requiresApprovalForDiscountStrategy() {
    assertThat(engine.requiresApproval(policy, 349_900, "BOUNDED_DISCOUNT", false)).isTrue();
  }

  @Test
  void requiresApprovalForLowConfidenceDiagnosis() {
    assertThat(engine.requiresApproval(policy, 349_900, "PAYMENT_LINK", true)).isTrue();
  }

  @Test
  void noApprovalForRoutineRetry() {
    assertThat(engine.requiresApproval(policy, 349_900, "DELAYED_RETRY", false)).isFalse();
  }

  @Test
  void windowCalculation() {
    Instant detected = Instant.now();
    assertThat(PolicyEngine.remainingWindow(policy, detected, detected)).isEqualTo(java.time.Duration.ofHours(72));
    assertThat(PolicyEngine.windowExpired(policy, detected.minusSeconds(1), detected.plus(java.time.Duration.ofHours(73)))).isTrue();
  }
}
