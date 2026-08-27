# RecoverAI — Product Specification

**Product:** AI Revenue Recovery & Payment Reliability Engine
**Status:** Implementation baseline (v1)
**Build target:** Razorpay Buildathon (Razorpay TEST MODE), architected as a production SaaS platform.

---

## 1. Problem

Merchants lose revenue every day to:

- failed payments (insufficient funds, expired cards, bank declines, network timeouts)
- failed subscription renewals (mandate cancellations, expired cards)
- abandoned checkouts
- customers who intend to pay but are never followed up

This revenue is *recoverable*, but most merchants:

1. never know it was lost (no detection)
2. don't know why it failed (no diagnosis)
3. don't know what to do about it (no strategy)
4. can't safely automate recovery (no guardrails)
5. can't measure what worked (no measurement)

## 2. Solution

RecoverAI closes the loop from revenue loss to recovery with a single platform:

```
Revenue at risk ──► detected ──► diagnosed ──► strategy selected ──► policy-bounded execution ──► state reconciled ──► money recovered ──► measured ──► audited
```

The product answers twelve business questions:

1. How much revenue is currently at risk?
2. Why is it at risk?
3. Which transactions are recoverable?
4. What should we do for each one?
5. What intervention was actually executed?
6. Why was that intervention selected?
7. Did it recover the money?
8. How much incremental revenue did RecoverAI recover?
9. Which strategies work best?
10. Which incidents remain unresolved?
11. Which actions were prevented by safety rules?
12. Can every automated financial decision be audited?

## 3. Primary metric

> **₹X Revenue Recovered** — headline of the Overview dashboard.

Supporting metrics: revenue at risk, incremental revenue vs baseline, recovery rate,
recoverable incidents, unresolved incidents, successful recoveries, recovery attempts,
strategy-wise success, average time to recovery, estimated recovery probability,
prevented unsafe actions, customer-contact count, retry count, intervention cost,
net recovered value.

## 4. Recovery flows

| Priority | Flow | Description |
|---|---|---|
| P0 | Failed payment recovery | payment failure → ingestion → reconciliation → incident → diagnosis → strategy → policy → execution → reconciliation → outcome |
| P0 | Subscription recovery | subscription lifecycle events, mandate failures; coordinates *platform-managed* retries (Razorpay), customer comms, payment-method update, recovery links, escalation, stopping rules |
| P1 | Checkout abandonment | CART_CREATED → CHECKOUT_STARTED → PAYMENT_NOT_ATTEMPTED → ABANDONED → RECOVERY_SCHEDULED → NUDGE_SENT → CHECKOUT_RESUMED → PAYMENT_COMPLETED |
| P2 | Promise-to-pay | "Salary comes Monday" → structured promise → durable follow-up workflow |
| P3 | Hinglish conversational recovery | text-first; voice-ready interfaces; never required for core product |

## 5. Non-functional requirements

- **Money correctness:** integer minor units (paise), `BIGINT` + `CURRENCY` everywhere. No floats.
- **AI safety:** *AI recommends. Deterministic software authorizes and executes.* LLM output is untrusted input, validated against strict schemas. AI has no financial state and cannot execute actions.
- **Idempotency:** every external side-effecting operation carries a deterministic idempotency key; duplicates are expected and harmless.
- **Auditability:** every meaningful action writes an immutable audit event with before/after state and decision snapshots.
- **Multi-tenancy:** every merchant-owned row is tenant-scoped; tenant context is resolved server-side from the authenticated principal, never from client input.
- **Fault tolerance:** bounded retries, circuit breakers, dead-letter queue, manual replay, deterministic fallbacks.
- **Graceful degradation:** AI provider down → deterministic diagnosis; Kafka down → outbox retained, replay on recovery; DB restart → workers resume from workflow state.

## 6. Demo mode

`DEMO_MODE=true` runs the entire platform without paid external services:

- demo organization + users + seeded synthetic incidents (deterministic, seeded RNG)
- deterministic AI fallback when no LLM key is configured
- demo notification provider renders messages into an in-app communication inbox
- Razorpay mock provider (fixture-based) when test keys are absent
- reproducible experiment engine (baseline vs RecoverAI)

Every simulated element is clearly labeled **SIMULATED / SYNTHETIC TEST-MODE** in the UI. No simulated result is ever presented as a real-world measurement.

## 7. Scope boundaries (v1)

- No live-money processing (Razorpay TEST MODE only).
- No real external communication channels (Email/SMS/WhatsApp adapters are interface-complete; the demo adapter renders into the inbox).
- No data warehouse (metric snapshots + materialized-view-ready queries; documented scaling path).
- Voice channel for conversational recovery is designed-for, not built.
