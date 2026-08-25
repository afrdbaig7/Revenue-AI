package com.recoverai.policy.application;

import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentStatus;
import com.recoverai.policy.domain.PolicySet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic policy engine — the authorization gate for every recovery action.
 * Evaluation results are recorded per incident and audited. The AI can neither call nor
 * override this engine.
 */
@Component
public class PolicyEngine {

  public record Check(String rule, boolean passed, String detail) {}

  public record Evaluation(boolean allowed, List<Check> checks, String blockingRule) {

    public static Evaluation allow(List<Check> checks) {
      return new Evaluation(true, checks, null);
    }

    public static Evaluation deny(String rule, List<Check> checks) {
      return new Evaluation(false, checks, rule);
    }
  }

  /**
   * Evaluate an action proposal against the active policy set and current state.
   * Hard stopping rules always win (payment recovered, opt-out, window expiry, limits).
   */
  public Evaluation evaluate(
      PolicySet policy,
      Payment payment,
      String strategy,
      int attemptsSoFar,
      int contactsSoFar,
      Instant now,
      long proposedAmountMinor) {

    List<Check> checks = new ArrayList<>();

    // --- Hard stopping rules (never overridable) ---
    if (payment != null && payment.getStatus().isCollectable()) {
      checks.add(new Check("PAYMENT_ALREADY_COLLECTED", false, "Payment is " + payment.getStatus()));
      return Evaluation.deny("PAYMENT_ALREADY_COLLECTED", checks);
    }
    if (payment != null && payment.getStatus() != PaymentStatus.FAILED) {
      checks.add(new Check("PAYMENT_NOT_FAILED", false, "Payment is " + payment.getStatus()));
      return Evaluation.deny("PAYMENT_NOT_FAILED", checks);
    }

    if (attemptsSoFar >= policy.getMaxRetries()) {
      checks.add(new Check("MAX_RETRIES", false, "attempts=" + attemptsSoFar + " max=" + policy.getMaxRetries()));
      return Evaluation.deny("MAX_RETRIES", checks);
    }
    if (contactsSoFar >= policy.getMaxContactAttempts()) {
      checks.add(
          new Check("MAX_CONTACTS", false, "contacts=" + contactsSoFar + " max=" + policy.getMaxContactAttempts()));
      return Evaluation.deny("MAX_CONTACTS", checks);
    }
    if (proposedAmountMinor < policy.getMinimumRecoverableAmount()) {
      checks.add(
          new Check(
              "MINIMUM_AMOUNT",
              false,
              "amount=" + proposedAmountMinor + " min=" + policy.getMinimumRecoverableAmount()));
      return Evaluation.deny("MINIMUM_AMOUNT", checks);
    }

    // --- Strategy channel/type gates ---
    switch (strategy) {
      case "PAYMENT_LINK", "DELAYED_RETRY", "ALTERNATE_PAYMENT_METHOD" -> {
        if (!policy.isAllowPaymentLinks()) {
          checks.add(new Check("PAYMENT_LINKS_PROHIBITED", false, "policy disallows payment links"));
          return Evaluation.deny("PAYMENT_LINKS_PROHIBITED", checks);
        }
      }
      case "EMAIL_NUDGE" -> {
        if (!policy.isAllowEmail()) {
          checks.add(new Check("EMAIL_PROHIBITED", false, "policy disallows email"));
          return Evaluation.deny("EMAIL_PROHIBITED", checks);
        }
      }
      case "SMS_NUDGE" -> {
        if (!policy.isAllowSms()) {
          checks.add(new Check("SMS_PROHIBITED", false, "policy disallows sms"));
          return Evaluation.deny("SMS_PROHIBITED", checks);
        }
      }
      case "WHATSAPP_NUDGE" -> {
        if (!policy.isAllowWhatsApp()) {
          checks.add(new Check("WHATSAPP_PROHIBITED", false, "policy disallows whatsapp"));
          return Evaluation.deny("WHATSAPP_PROHIBITED", checks);
        }
      }
      case "BOUNDED_DISCOUNT" -> {
        if (!policy.isAllowDiscounts()) {
          checks.add(new Check("DISCOUNTS_PROHIBITED", false, "policy disallows discounts"));
          return Evaluation.deny("DISCOUNTS_PROHIBITED", checks);
        }
        long maxDiscount = proposedAmountMinor * policy.getMaxDiscountPercent() / 100;
        checks.add(new Check("DISCOUNT_BOUND", true, "discount <= " + policy.getMaxDiscountPercent() + "% (" + maxDiscount + ")"));
      }
      default -> {
        // NO_ACTION, WAIT_FOR_PROVIDER_RETRY, PROMISE_TO_PAY, MANUAL_ESCALATION: no channel gate
      }
    }

    // --- Time window ---
    if (policy.getRecoveryWindowHours() > 0) {
      checks.add(new Check("WINDOW", true, "window=" + policy.getRecoveryWindowHours() + "h"));
    }

    checks.add(new Check("RETRY_BOUND", true, "attempts " + attemptsSoFar + " < max " + policy.getMaxRetries()));
    checks.add(new Check("CONTACT_BOUND", true, "contacts " + contactsSoFar + " < max " + policy.getMaxContactAttempts()));
    return Evaluation.allow(checks);
  }

  /** Does this proposal require human approval? */
  public boolean requiresApproval(PolicySet policy, long amountMinor, String strategy, boolean lowConfidence) {
    boolean highValue = amountMinor >= policy.getRequireApprovalAboveAmount();
    boolean approvalStrategy =
        strategy.equals("BOUNDED_DISCOUNT") || strategy.equals("MANUAL_ESCALATION");
    return highValue || approvalStrategy || lowConfidence;
  }

  public static Duration remainingWindow(PolicySet policy, Instant detectedAt, Instant now) {
    Instant endsAt = detectedAt.plus(Duration.ofHours(policy.getRecoveryWindowHours()));
    Duration remaining = Duration.between(now, endsAt);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  /** Backward-compatible helper used by callers that hold an incident. */
  public static boolean windowExpired(PolicySet policy, Instant detectedAt, Instant now) {
    return remainingWindow(policy, detectedAt, now).isZero();
  }
}
