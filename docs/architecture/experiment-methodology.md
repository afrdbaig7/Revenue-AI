# Experiment Methodology — Baseline vs RecoverAI

## Goal

Demonstrate (on **synthetic** data) that RecoverAI's strategy selection recovers more
revenue than a fixed baseline, with fewer unnecessary interventions. The results are
**not** evidence of real-world causal uplift — they are a reproducible simulation used
for evaluation and demos, and every report is labeled
**SIMULATED / SYNTHETIC TEST-MODE RESULTS**.

## Population

A seeded RNG (`seed`, default 42) generates N incidents with:

- Amounts drawn from a lognormal-ish price distribution (₹499–₹49,999, paise integers).
- Failure categories across all 10 cohorts (insufficient funds, card expired, card
  blocked, bank decline, network timeout, mandate failure, checkout abandoned,
  authentication failure, processor error, unknown).
- A latent per-incident recoverability (15–70%) and a "time until funds arrive" for
  balance-related failures.
- **14% truly unrecoverable** incidents — no strategy can recover them. This prevents
  inflated, unrealistic win rates.

## Arms (same population, both run fully)

**CONTROL — fixed baseline:**
- Blind retry after N hours (default 24), fixed number of attempts (2–3).
- A contact on every attempt (no channel intelligence).
- No failure-category awareness, no timing.

**TREATMENT — RecoverAI:**
- Category-aware delay (e.g., insufficient funds retried just after expected funds
  arrival; timeouts retried quickly).
- Category-fit multipliers (payment links fit card-expired; retries fit transient).
- Bounded contacts (max 2), policy guardrail that occasionally blocks marginal retries.
- Attempt cost ₹150 (baseline) vs ₹100 (treatment) per attempt.

Both arms share the same latent recoverability per incident, so any difference comes
from *selection and timing*, not from rigged outcomes.

## Outputs (per arm + delta)

Revenue at risk · gross recovered · net recovered (after intervention cost) ·
recovery rate with Wilson 95% confidence interval · success/failure counts ·
average attempts · average contacts · average time to recovery · intervention cost ·
policy blocks · unnecessary contacts.

## Reproducibility

Same seed ⇒ identical results (asserted in `ExperimentSimulatorTest`). The simulator is
a pure function of (seed, N, config). Experiments are persisted (`experiments`,
`experiment_assignments`) and downloadable as JSON or CSV from the Experiments page or
`POST /api/v1/experiments`.

## Known limits

- Synthetic population model — not calibrated to any real merchant.
- No interaction effects (e.g., customers annoyed by repeated contacts).
- Single-pass simulation; no multi-period learning.
- The treatment's edge (~20–25 pts recovery rate in the seeded model) is an artifact of
  the model's assumptions, not a measured real-world claim.
