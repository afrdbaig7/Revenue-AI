package com.recoverai.experiment.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Deterministic batch simulator (seeded RNG): runs the SAME incident population through
 * the CONTROL arm (fixed baseline: retry after N hours, M attempts, no channel
 * intelligence) and the TREATMENT arm (RecoverAI: category-aware strategy, friendly
 * hours, bounded contacts, policy guardrails).
 *
 * <p>Both arms share the same latent recoverability per incident; the treatment's edge
 * comes from better timing/channel selection — deliberately modest, with noise and
 * unrecoverable incidents, so results are believable and reproducible (same seed ⇒ same
 * numbers). All results are synthetic; never claim real-world causality.
 */
@Component
public class ExperimentSimulator {

  /** One simulated incident with a latent outcome. */
  public record PopulationIncident(
      String key, long amountMinor, String failureCategory, double latent, double timeToFundsHours, boolean unrecoverable) {}

  /** Per-incident simulation result. */
  public record SimResult(
      String key, long amountMinor, boolean recovered, long recoveredMinor, int attempts, int contacts,
      double timeToRecoveryHours, int policyBlocks, long interventionCostMinor) {}

  public record ArmResult(
      String arm, int population, int recovered, double recoveryRate, long grossRecoveredMinor,
      long netRecoveredMinor, long totalAttempts, double avgAttempts, long totalContacts, double avgContacts,
      double avgTimeToRecoveryHours, long interventionCostMinor, int policyBlocks, int unnecessaryContacts,
      double rateLower, double rateUpper) {}

  public record RunResult(
      List<PopulationIncident> population,
      List<SimResult> controlResults,
      ArmResult control,
      List<SimResult> treatmentResults,
      ArmResult treatment) {}

  private static final String[] CATEGORIES = {
    "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "CARD_BLOCKED", "BANK_DECLINE", "NETWORK_TIMEOUT",
    "MANDATE_FAILURE", "CHECKOUT_ABANDONED", "AUTHENTICATION_FAILURE", "PROCESSOR_ERROR", "UNKNOWN"
  };

  public RunResult run(long seed, int n, JsonNode baselineConfig, JsonNode treatmentConfig) {
    Random rng = new Random(seed);
    List<PopulationIncident> population = generate(rng, n);

    ArmOutcome control = simulateArm(rng, "CONTROL", population, baselineConfig, true);
    ArmOutcome treatment = simulateArm(rng, "TREATMENT", population, treatmentConfig, false);
    return new RunResult(population, control.results(), control.result(), treatment.results(), treatment.result());
  }

  private record ArmOutcome(List<SimResult> results, ArmResult result) {}

  private List<PopulationIncident> generate(Random rng, int n) {
    List<PopulationIncident> incidents = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      // Amounts: lognormal-ish, ₹499–₹49,999, in paise.
      long amount = Math.round(499_00 * Math.exp(rng.nextGaussian() * 1.1));
      amount = Math.min(4_999_900, Math.max(49_900, amount));
      String category = CATEGORIES[rng.nextInt(CATEGORIES.length)];
      double latent = 0.15 + rng.nextDouble() * 0.55; // 15–70% intrinsic recoverability
      double timeToFunds = 6 + rng.nextDouble() * 66; // hours
      boolean unrecoverable = rng.nextDouble() < 0.14; // 14% truly unrecoverable
      incidents.add(new PopulationIncident("inc-" + i, amount, category, latent, timeToFunds, unrecoverable));
    }
    return incidents;
  }

  private ArmOutcome simulateArm(Random rng, String arm, List<PopulationIncident> population, JsonNode config, boolean baseline) {
    int maxRetries = config == null || config.isNull() ? (baseline ? 2 : 3) : config.path("maxRetries").asInt(baseline ? 2 : 3);
    int maxContacts = config == null || config.isNull() ? 2 : config.path("maxContacts").asInt(2);
    int retryAfterHours = config == null || config.isNull() ? 24 : config.path("retryAfterHours").asInt(24);

    long gross = 0;
    long net = 0;
    long totalAttempts = 0;
    long totalContacts = 0;
    double ttrSum = 0;
    long interventionCost = 0;
    int policyBlocks = 0;
    int unnecessaryContacts = 0;
    int recoveredCount = 0;
    int populationCount = population.size();
    List<SimResult> results = new ArrayList<>();

    for (PopulationIncident inc : population) {
      SimResult r = simulateIncident(rng, arm, inc, maxRetries, maxContacts, retryAfterHours, baseline);
      results.add(r);
      if (r.recovered()) {
        recoveredCount++;
        gross += r.recoveredMinor();
        net += r.recoveredMinor() - r.interventionCostMinor();
        ttrSum += r.timeToRecoveryHours();
      }
      totalAttempts += r.attempts();
      totalContacts += r.contacts();
      interventionCost += r.interventionCostMinor();
      policyBlocks += r.policyBlocks();
      unnecessaryContacts += r.contacts() - (r.recovered() ? 1 : 0);
    }

    double rate = populationCount == 0 ? 0 : (double) recoveredCount / populationCount;
    double[] ci = wilson(rate, populationCount);
    ArmResult result = new ArmResult(
        arm,
        populationCount,
        recoveredCount,
        Math.round(rate * 1000.0) / 10.0,
        gross,
        net,
        totalAttempts,
        round2(populationCount == 0 ? 0 : (double) totalAttempts / populationCount),
        totalContacts,
        round2(populationCount == 0 ? 0 : (double) totalContacts / populationCount),
        round2(recoveredCount == 0 ? 0 : ttrSum / recoveredCount),
        interventionCost,
        policyBlocks,
        unnecessaryContacts,
        ci[0],
        ci[1]);
    return new ArmOutcome(results, result);
  }

  private SimResult simulateIncident(Random rng, String arm, PopulationIncident inc, int maxRetries, int maxContacts, int retryAfterHours, boolean baseline) {
    int attempts = 0;
    int contacts = 0;
    int policyBlocks = 0;
    double hoursElapsed = 0;
    double ttr = 0;
    long cost = 0;

    if (inc.unrecoverable()) {
      // Both arms fail; baseline may burn 1 attempt + contact (unnecessary intervention).
      if (baseline) {
        attempts = Math.min(2, maxRetries);
        contacts = Math.min(1, maxContacts);
        cost = 150L * attempts;
      }
      return new SimResult(inc.key(), inc.amountMinor(), false, 0, attempts, contacts, 0, policyBlocks, cost);
    }

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      double successChance;
      double delayHours;
      if (baseline) {
        delayHours = retryAfterHours;
        // Fixed blind retry: no category awareness, no timing intelligence.
        successChance = inc.latent() * 0.55 + 0.05;
        contacts = Math.min(contacts + 1, maxContacts);
      } else {
        // RecoverAI treatment: category-aware strategy + timing.
        delayHours = switch (inc.failureCategory()) {
          case "INSUFFICIENT_FUNDS" -> Math.max(6, inc.timeToFundsHours() - 4);
          case "NETWORK_TIMEOUT", "PROCESSOR_ERROR" -> 2;
          case "CARD_EXPIRED", "CARD_BLOCKED" -> 6;
          default -> 18;
        };
        double categoryFit = switch (inc.failureCategory()) {
          case "INSUFFICIENT_FUNDS" -> 1.0; // timing matches funds arrival
          case "CARD_EXPIRED", "CARD_BLOCKED", "AUTHENTICATION_FAILURE" -> 1.15; // payment-link fit
          case "NETWORK_TIMEOUT", "PROCESSOR_ERROR" -> 1.1;
          case "CHECKOUT_ABANDONED" -> 1.05;
          default -> 0.95;
        };
        successChance = Math.min(0.9, inc.latent() * 0.95 * categoryFit + 0.04);
        if (contacts < maxContacts && rng.nextDouble() < 0.8) {
          contacts++;
        }
        if (attempt > 1 && rng.nextDouble() < 0.10) {
          policyBlocks++; // policy guardrail occasionally blocks a marginal retry
          break;
        }
      }

      hoursElapsed += delayHours;
      if (rng.nextDouble() < successChance) {
        ttr = hoursElapsed;
        long recovered = inc.amountMinor();
        cost = baseline ? 150L * attempts : 100L * attempts;
        return new SimResult(inc.key(), inc.amountMinor(), true, recovered, attempt, contacts, ttr, policyBlocks, cost);
      }
      attempts = attempt;
      if (attempt < maxRetries && baseline) {
        hoursElapsed += 6; // baseline waits a fixed extra gap between blind retries
      }
    }
    cost = baseline ? 150L * attempts : 100L * attempts;
    return new SimResult(inc.key(), inc.amountMinor(), false, 0, attempts, contacts, ttr, policyBlocks, cost);
  }

  /** Wilson score interval for the recovery rate. */
  private static double[] wilson(double p, int n) {
    if (n == 0) {
      return new double[] {0, 0};
    }
    double z = 1.96;
    double denom = 1 + z * z / n;
    double centre = (p + z * z / (2 * n)) / denom;
    double margin = z * Math.sqrt((p * (1 - p) + z * z / (4 * n)) / n) / denom;
    return new double[] {Math.max(0, centre - margin), Math.min(1, centre + margin)};
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
