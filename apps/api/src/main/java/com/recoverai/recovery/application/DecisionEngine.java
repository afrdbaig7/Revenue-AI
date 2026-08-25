package com.recoverai.recovery.application;

import com.recoverai.incident.domain.IncidentType;
import com.recoverai.payment.domain.FailureCategory;
import com.recoverai.recovery.domain.RecoveryDecision.CandidateView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Expected-value engine:
 *
 * <pre>
 * EV = P(recovery) × NetRecoverableAmount − InterventionCost − DiscountCost − FrictionPenalty − RiskPenalty
 * </pre>
 *
 * Probabilities are calibrated from seeded historical simulation (synthetic) and are
 * labeled as estimates; deterministic, auditable, and explainable.
 */
@Component
public class DecisionEngine {

  public record EvInput(
      long amountMinor,
      FailureCategory category,
      IncidentType incidentType,
      String paymentMethod,
      boolean businessHours) {}

  public List<CandidateView> scoreCandidates(List<CandidateGenerator.CandidateSeed> seeds, EvInput input) {
    List<CandidateView> scored = new ArrayList<>();
    for (CandidateGenerator.CandidateSeed seed : seeds) {
      scored.add(score(seed, input));
    }
    scored.sort(Comparator.comparingLong(CandidateView::expectedValueMinor).reversed());
    return scored;
  }

  private CandidateView score(CandidateGenerator.CandidateSeed seed, EvInput input) {
    long amount = input.amountMinor();
    double base = baseProbability(seed.strategy(), input);
    double probability = clamp(base + timeBoost(seed.strategy(), input.businessHours()));
    long gross = Math.round(amount * probability);
    long interventionCost = interventionCost(seed.strategy());
    long discountCost = discountCost(seed.strategy(), amount);
    long friction = frictionPenalty(seed.strategy(), amount);
    long risk = riskPenalty(seed.strategy());
    long expectedValue = gross - interventionCost - discountCost - friction - risk;
    if (expectedValue < 0) {
      expectedValue = 0;
    }
    return new CandidateView(
        seed.strategy(),
        round4(probability),
        expectedValue,
        gross,
        interventionCost,
        discountCost,
        risk,
        friction,
        timeToRecoveryHours(seed.strategy()),
        seed.rationale());
  }

  private double baseProbability(String strategy, EvInput input) {
    // Calibrated on synthetic seeded simulations; deliberately modest — no magic wins.
    return switch (strategy) {
      case "DELAYED_RETRY" -> input.category().isTransient() ? 0.42 : 0.18;
      case "PAYMENT_LINK" -> switch (input.category()) {
        case CARD_EXPIRED, CARD_BLOCKED, MANDATE_CANCELLED, MANDATE_FAILURE -> 0.52;
        case INSUFFICIENT_FUNDS, NETWORK_TIMEOUT -> 0.34;
        default -> 0.22;
      };
      case "UPI_RECOVERY" ->
          input.paymentMethod() != null && input.paymentMethod().equalsIgnoreCase("upi") ? 0.40 : 0.20;
      case "EMAIL_NUDGE" -> input.incidentType() == IncidentType.CHECKOUT_ABANDONMENT ? 0.30 : 0.16;
      case "WHATSAPP_NUDGE" -> 0.24;
      case "SMS_NUDGE" -> 0.12;
      case "ALTERNATE_PAYMENT_METHOD" -> 0.28;
      case "WAIT_FOR_PROVIDER_RETRY" -> 0.20;
      case "BOUNDED_DISCOUNT" -> 0.38;
      case "PROMISE_TO_PAY" -> 0.30;
      case "MANUAL_ESCALATION" -> 0.35;
      default -> 0.0;
    };
  }

  private double timeBoost(String strategy, boolean businessHours) {
    // A retry at a customer-friendly hour converts better (synthetic calibration).
    if ((strategy.equals("DELAYED_RETRY") || strategy.equals("PAYMENT_LINK")) && businessHours) {
      return 0.06;
    }
    return 0.0;
  }

  private long interventionCost(String strategy) {
    return switch (strategy) {
      case "PAYMENT_LINK", "UPI_RECOVERY" -> 100;
      case "EMAIL_NUDGE" -> 50;
      case "WHATSAPP_NUDGE" -> 100;
      case "SMS_NUDGE" -> 150;
      case "BOUNDED_DISCOUNT" -> 200;
      case "MANUAL_ESCALATION" -> 500;
      default -> 0;
    };
  }

  private long discountCost(String strategy, long amount) {
    return strategy.equals("BOUNDED_DISCOUNT") ? Math.min(amount / 10, 5_00_00) : 0; // ≤10%, capped
  }

  private long frictionPenalty(String strategy, long amount) {
    return switch (strategy) {
      case "EMAIL_NUDGE" -> Math.max(50, amount / 2000);
      case "WHATSAPP_NUDGE" -> Math.max(75, amount / 1500);
      case "SMS_NUDGE" -> Math.max(60, amount / 1800);
      default -> 0;
    };
  }

  private long riskPenalty(String strategy) {
    return switch (strategy) {
      case "DELAYED_RETRY" -> 150; // double-charge risk, late-auth risk
      case "BOUNDED_DISCOUNT" -> 100;
      case "MANUAL_ESCALATION" -> 100;
      default -> 0;
    };
  }

  private int timeToRecoveryHours(String strategy) {
    return switch (strategy) {
      case "PAYMENT_LINK", "UPI_RECOVERY" -> 6;
      case "EMAIL_NUDGE" -> 18;
      case "WHATSAPP_NUDGE" -> 12;
      case "SMS_NUDGE" -> 24;
      case "DELAYED_RETRY" -> 30;
      case "WAIT_FOR_PROVIDER_RETRY" -> 48;
      case "PROMISE_TO_PAY" -> 36;
      default -> 72;
    };
  }

  private static double clamp(double v) {
    return Math.max(0.01, Math.min(0.85, v));
  }

  private static double round4(double v) {
    return Math.round(v * 10_000.0) / 10_000.0;
  }
}
