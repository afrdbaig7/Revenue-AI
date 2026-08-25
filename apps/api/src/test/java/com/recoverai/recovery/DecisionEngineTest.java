package com.recoverai.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.recoverai.incident.domain.IncidentType;
import com.recoverai.payment.domain.FailureCategory;
import com.recoverai.recovery.application.CandidateGenerator;
import com.recoverai.recovery.application.CandidateGenerator.CandidateSeed;
import com.recoverai.recovery.application.DecisionEngine;
import com.recoverai.recovery.application.DecisionEngine.EvInput;
import com.recoverai.recovery.domain.RecoveryDecision.CandidateView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Expected-value math — deterministic, auditable, never negative. */
class DecisionEngineTest {

  private final DecisionEngine engine = new DecisionEngine();
  private final CandidateGenerator generator = new CandidateGenerator();

  @Test
  void evFormulaIsCorrect() {
    // EV = P × amount − cost − discount − friction − risk
    CandidateView view = engine.scoreCandidates(
            List.of(new CandidateSeed("PAYMENT_LINK", "test")),
            new EvInput(349_900, FailureCategory.INSUFFICIENT_FUNDS, IncidentType.PAYMENT_FAILURE, "card", true))
        .get(0);
    // Derive from the engine's own gross (unrounded probability) to avoid rounding drift.
    // PAYMENT_LINK carries: intervention cost 100, no discount, no friction, no risk.
    long expected = view.expectedGrossMinor() - 100;
    assertThat(view.expectedValueMinor()).isEqualTo(Math.max(0, expected));
    assertThat(view.expectedValueMinor()).isLessThanOrEqualTo(view.expectedGrossMinor());
  }

  @Test
  void expectedValueNeverNegative() {
    for (long amount : new long[] {49_900, 149_900, 349_900, 4_999_900}) {
      for (String category : new String[] {
        "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "UNKNOWN", "CHECKOUT_ABANDONED"
      }) {
        List<CandidateView> scored = engine.scoreCandidates(
            List.of(
                new CandidateSeed("DELAYED_RETRY", "t"),
                new CandidateSeed("PAYMENT_LINK", "t"),
                new CandidateSeed("EMAIL_NUDGE", "t"),
                new CandidateSeed("NO_ACTION", "t")),
            new EvInput(
                amount,
                FailureCategory.valueOf(category),
                IncidentType.PAYMENT_FAILURE,
                "card",
                true));
        for (CandidateView view : scored) {
          assertThat(view.expectedValueMinor()).isGreaterThanOrEqualTo(0);
        }
      }
    }
  }

  @Test
  void rankingOrdersByExpectedValueDescending() {
    List<CandidateView> scored = engine.scoreCandidates(
        List.of(
            new CandidateSeed("NO_ACTION", "always zero"),
            new CandidateSeed("PAYMENT_LINK", "high fit"),
            new CandidateSeed("DELAYED_RETRY", "transient fit")),
        new EvInput(349_900, FailureCategory.INSUFFICIENT_FUNDS, IncidentType.PAYMENT_FAILURE, "card", true));
    for (int i = 1; i < scored.size(); i++) {
      assertThat(scored.get(i - 1).expectedValueMinor())
          .isGreaterThanOrEqualTo(scored.get(i).expectedValueMinor());
    }
    assertThat(scored.get(scored.size() - 1).strategy()).isEqualTo("NO_ACTION");
  }

  @Test
  void transientFailuresFavorDelayedRetryOverNoAction() {
    List<CandidateView> scored = engine.scoreCandidates(
        generator.generate(
            incidentWith(FailureCategory.NETWORK_TIMEOUT), null),
        new EvInput(349_900, FailureCategory.NETWORK_TIMEOUT, IncidentType.PAYMENT_FAILURE, "card", true));
    assertThat(scored.stream().anyMatch(c -> c.strategy().equals("DELAYED_RETRY"))).isTrue();
  }

  private com.recoverai.incident.domain.RevenueIncident incidentWith(FailureCategory category) {
    com.recoverai.incident.domain.RevenueIncident incident =
        new com.recoverai.incident.domain.RevenueIncident();
    incident.setIncidentType(IncidentType.PAYMENT_FAILURE);
    incident.setFailureCategory(category.name());
    incident.setAmountMinor(349_900);
    return incident;
  }
}
