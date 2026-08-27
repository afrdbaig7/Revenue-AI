# RecoverAI — Buildathon Demo Script (10 scenes)

> All figures below are **SIMULATED / SYNTHETIC TEST-MODE** results unless stated.
> Run: `make setup && make infra-up && make migrate && make seed && make dev`

## Scene 1 — Dashboard (10-second value)
Overview page shows:
- **Revenue Recovered ₹2,84,300** (headline)
- Revenue at Risk ₹5,24,000 · Incremental Revenue ₹1,13,300 · Recovery Rate 54.3%
- Charts: recovered revenue over time, at-risk over time, recovery by strategy, by failure reason
- Banner: `SIMULATED RESULTS — SYNTHETIC TEST-MODE DATA`

## Scene 2 — Open a failed payment
Incidents → pick `INSUFFICIENT_FUNDS` incident, ₹3,499.
Detail shows: failure category, **RecoverAI diagnosis 91% confidence**, evidence list
(provider code mapping, 3 prior balance failures, previous evening successes).

## Scene 3 — Candidate strategies
"Retry now EV ₹714 · Retry tomorrow 18:30 EV ₹2,102 · Payment-link message EV ₹2,248"
with probability, cost, time-to-recovery per candidate.

## Scene 4 — AI-selected strategy
Selected: **PAYMENT_LINK** with model-versioned rationale (or deterministic fallback
`layer=DETERMINISTIC` when no LLM key — same UI, labeled).

## Scene 5 — Policy engine approval
Policy evaluation panel: rules checked (max retries ✓, contact cooldown ✓, window ✓,
discount ✓) → `POLICY PASS`. High-value incident instead routes to **Approval Queue**.

## Scene 6 — Execute recovery flow (Razorpay test mode)
Worker executes via Razorpay Adapter: creates a payment link (real test-mode call when
keys configured; mock provider otherwise, labeled SIMULATED). Action → `EXECUTING`.

## Scene 7 — Webhook arrives
`payment.authorized` webhook hits `/api/v1/webhooks/razorpay`; signature verified;
inbox row recorded; incident detail timeline shows `PAYMENT_EVENT_RECEIVED` →
`PAYMENT_RECONCILED` → `PAYMENT_RECOVERED`. (Live demo: trigger from Razorpay test
dashboard or use the scripted `scripts/demo/fire-events.sh`.)

## Scene 8 — Incident RECOVERED; dashboard updates
Status → `RECOVERED`, recovered ₹3,499, headline ticks up, audit rows appended.

## Scene 9 — Failure handling: late authorization
Incident looked `FAILED` → recovery scheduled → `payment.authorized` arrives late →
timeline: `LATE_AUTHORIZATION_RECEIVED`, `RECOVERY CANCELLED` ("Payment became
authorized before recovery execution. Duplicate collection prevented."), incident →
`LATE_AUTHORIZED` → `CLOSED`. Metrics: duplicate-collection-prevented +1.

## Scene 10 — Immutable audit trail
Audit Log page: full decision history for the incident (detection → diagnosis →
candidates → policy → action → webhook → outcome) with actor, correlation ID,
before/after state, snapshots. No edit/delete possible.

### Also demo
- **Approval Queue** (high-value incident, approve/reject with recorded actor)
- **Experiments**: baseline vs RecoverAI, same seeded population, CSV/JSON report
- **Policy blocks**: discount over limit → `POLICY_BLOCKED`, visible in timeline
- **System Health**: webhook processing, DLQ, AI status, provider status
