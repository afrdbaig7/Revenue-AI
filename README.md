# RecoverAI

**AI Revenue Recovery & Payment Reliability Engine**

RecoverAI detects revenue that is at risk of being lost to failed payments, failed
subscriptions, and abandoned checkouts — then diagnoses **why**, selects the **safest
recovery strategy**, executes only **policy-permitted** actions, reconciles the real
payment state, and reports **exactly how much revenue was recovered**.

> **AI recommends. Deterministic software authorizes and executes.**

Built for the Razorpay Buildathon on Razorpay **TEST MODE**, architected as a
production SaaS platform: event-driven, multi-tenant, idempotent, auditable, and
observable end to end.

---

## Problem

Merchants lose revenue every day to payment failures they never see, never understand,
and never act on. RecoverAI closes that loop.

## Solution

```
revenue at risk → detected → diagnosed → strategy selected
→ policy-bounded execution → state reconciled → money recovered → measured → audited
```

- **Detection** — webhooks (signature-verified, idempotent, order-tolerant) and
  checkout/subscription lifecycle tracking
- **Diagnosis** — deterministic failure taxonomy + optional AI reasoning with
  confidence and evidence
- **Decision** — expected-value-ranked strategies (`EV = P(recovery) × net amount − costs − risk`)
- **Bounded execution** — deterministic policy engine, human-in-the-loop approvals,
  hard stopping rules the AI cannot override
- **Reconciliation** — late-authorization handling prevents double collection
- **Measurement** — batch experiment engine: RecoverAI vs fixed baseline, on the same
  seeded population, clearly labeled **SIMULATED / SYNTHETIC TEST-MODE**
- **Auditability** — immutable audit ledger for every decision and action

## Key features

| Area | Highlights |
|---|---|
| Payments | `PaymentProvider` abstraction, Razorpay test-mode adapter, normalized payment state machine, late-authorization protocol |
| Recovery | 12 strategies, incident state machine (18 states), EV decision engine, expected-value candidates, strategy catalog |
| Safety | Policy engine (retries/contacts/discounts/windows/approval thresholds), stopping rules, approval queue, customer opt-outs, idempotency keys |
| AI | Separate FastAPI decision service, provider-agnostic (OpenAI/Gemini/Groq), strict Pydantic output schemas, deterministic fallback, circuit breaker, **no financial write path** |
| Events | Transactional outbox → Redpanda/Kafka, DLQ with idempotent replay, out-of-order tolerance |
| Workflows | **Temporal workflows (recovery + promise-to-pay)** with DB-backed scheduler fallback (demo); both converge on idempotent activities |
| Multi-tenancy | Org-scoped data, RBAC (OWNER/ADMIN/OPERATOR/ANALYST/VIEWER), server-side tenant resolution |
| Auth | Email/password (BCrypt), JWT access + rotating refresh tokens, HttpOnly cookies, CSRF, no localStorage tokens |
| Observability | Structured JSON logs, correlation IDs, Prometheus metrics, Grafana dashboards, **OpenTelemetry tracing (traceId in audit ledger + logs)** |
| Analytics | Metric snapshots (no hot-table scans), strategy/failure analytics, experiments with CSV/JSON reports |
| Demo | `DEMO_MODE=true` runs everything without paid services: deterministic AI, mock Razorpay, demo notification inbox, seeded incidents, reproducible experiments |

## Architecture

```text
Next.js dashboard → Spring Boot API (control plane) → PostgreSQL (system of record)
                      │                                   │
                      ├─ Redis (locks/limits/cache)        └─ transactional outbox
                      └─ Razorpay adapter (test mode)           │
                                                         Redpanda/Kafka
                                                              │
                                        Recovery Worker ──► Temporal workflows
                                              │  ├──► AI Decision Service (FastAPI)
                                              │  ├──► Policy Engine
                                              │  ├──► Notification adapters
                                              └──► Razorpay Adapter
```

Status: [honest audit vs the master build prompt](docs/implementation-status.md) ·
Docs: [system overview](docs/architecture/system-overview.md) · [data model](docs/architecture/data-model.md) ·
[payment state machine](docs/architecture/payment-state-machine.md) · [recovery state machine](docs/architecture/recovery-state-machine.md) ·
[event flow](docs/architecture/event-flow.md) · [security model](docs/architecture/security-model.md) ·
[scaling](docs/architecture/scaling.md) · [ADRs](docs/adr/) · [runbooks](docs/runbooks/) · [demo script](docs/demo/demo-script.md)

## Quick start

```bash
git clone <repo> && cd recoverai
cp .env.example .env

docker compose up -d               # postgres, redis, redpanda, temporal, prometheus, grafana
make setup                         # installs web + AI-service deps
make migrate                       # Flyway migrations
make seed                          # demo org + synthetic incidents + metrics
make dev                           # API :8080, AI :8100, Web :3000
```

Then:

- Dashboard → http://localhost:3000 (login `demo@recoverai.dev` / `DemoPass!123`)
- API docs → http://localhost:8080/swagger-ui
- AI service docs → http://localhost:8100/docs
- Grafana → http://localhost:3001 (admin/admin) · Prometheus → http://localhost:9090
- Temporal UI → http://localhost:8088

**No Docker?** PostgreSQL/Redis can run natively; set `EVENT_DISPATCH_MODE=inline`,
`TEMPORAL_ENABLED=false`, `RAZORPAY_MOCK_MODE=true` — everything still works.

## Environment variables

See [`.env.example`](.env.example). Groups: `DATABASE_*`, `REDIS_*`, `KAFKA_*`,
`TEMPORAL_*`, `RAZORPAY_*` (test mode), `LLM_*` (optional), `DEMO_MODE`, `JWT_*`,
`ENCRYPTION_KEY`. **Never commit real credentials** — see `SECURITY.md`.

## Payment safety model

1. Every action reconciles current payment state before executing.
2. Late authorization cancels pending recovery — duplicate collection is prevented.
3. Policy engine evaluates every action: retries, contacts, discounts, windows,
   approval thresholds, opt-outs — AI cannot override.
4. Idempotency keys make every retry/action/notification repeat-safe.
5. Webhook signatures are verified before parsing; payloads are redacted at rest.
6. Every transition and decision writes an immutable audit event.

## AI design

The AI service is advisory: it classifies failures, ranks strategies, explains
decisions, drafts communications, and extracts promise-to-pay intents. Output is
validated against strict schemas (Pydantic); malformed, slow, or low-confidence output
falls back to the deterministic engine. The AI has **no write path** to financial state
(see [ADR-006](docs/adr/ADR-006-ai-advisory-system.md)).

## Razorpay integration

Encapsulated behind `PaymentProvider`. Real endpoints used (test mode):
`GET /v1/payments/{id}`, `POST /v1/orders`, `POST /v1/payment_links`,
webhook signature verification (HMAC-SHA256 of the raw body). When test keys are
absent (`RAZORPAY_MOCK_MODE=true`), a fixture-based mock provider is used and every
result is labeled SIMULATED.

## Event processing

Transactional outbox → Redpanda/Kafka → workers. Webhook inbox dedupes by
`(provider, provider_event_id)`, tolerates out-of-order delivery via reconciliation,
and pushes permanent failures to a DLQ with idempotent replay.

## Experiment methodology

A seeded, deterministic simulator runs the **same incident population** through:
- **Control** — fixed retry after N hours, fixed attempts, no channel intelligence
- **Treatment** — RecoverAI selection, policy, bounded contacts

Outputs: recovery rate (with Wilson 95% CI), gross/net recovered, attempts, contacts,
time-to-recovery, unnecessary contacts, policy blocks (see
[experiment methodology](docs/architecture/experiment-methodology.md)). Results are
**synthetic**; the UI labels them as such. No causal real-world claims.

### Measured results (synthetic, seed 42, N = 10,000 — reproducible via `make experiment`)

| Metric | Control (baseline) | Treatment (RecoverAI) | Δ |
|---|---|---|---|
| Recovery rate (95% CI) | 41.4% (40.4–42.4) | 65.7% (64.8–66.6) | **+24.3 pts** |
| Net recovered | ₹42,96,857 | ₹67,86,857 | **+₹24,90,000** |
| Total attempts | 17,470 | 14,785 | −2,685 |
| Total contacts | 16,047 | 11,105 | −4,942 |
| Avg time to recovery | slower (blind 24h gaps) | category-aware timing | faster |

> These are model outputs from a seeded synthetic simulator — believable by design,
> never presented as real-world measurements. The UI labels all of them
> **SIMULATED / SYNTHETIC TEST-MODE RESULTS**.

## Testing

```bash
make test      # backend unit (69) + AI service tests (18)
make test-it   # backend integration tests (Testcontainers; requires Docker)
cd apps/web && npm run test      # web unit (6)
cd apps/web && npx playwright test   # E2E: login → dashboard → incident → approvals → audit
make bench     # k6 load scenarios (requires k6)
```

## Observability

Structured JSON logs with `correlationId` across browser → API → event → worker → AI →
provider; Prometheus metrics (`http_request_duration`, `webhook_*`, `kafka_consumer_lag`,
`ai_*`, `razorpay_*`, `recovery_*`); Grafana dashboards in
`infrastructure/monitoring/grafana/dashboards`.

## Security

See [`SECURITY.md`](SECURITY.md) and [security model](docs/architecture/security-model.md).

## Scaling

See [scaling.md](docs/architecture/scaling.md): modular monolith → read replicas →
partitioning → warehouse; Kafka partitions keyed by tenant; Temporal Cloud;
Kubernetes/Helm manifests in `infrastructure/`.

## Limitations (honest)

- Test-mode only; no live money.
- Email/SMS/WhatsApp adapters are interface-complete; the demo provider renders into
  an in-app inbox (never mislabeled as real).
- Synthetic data only — no real PII; results are simulated and labeled.
- Performance numbers are documented as measured, never fabricated
  (`docs/performance.md`).

## Roadmap

P3 Hinglish conversational recovery (text-first, voice-ready) · SSO · warehouse
analytics · more provider adapters (Stripe, etc.) · SMS/WhatsApp/Email production
gateways · UUIDv7 on PG18 · rate-limit admin UI.

## License

MIT — see [LICENSE](LICENSE).
