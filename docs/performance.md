# RecoverAI — Performance

> Principle: performance claims are **measured**, never fabricated. This page records
> the numbers as they were observed on the development environment, with the method
> used to obtain them. Benchmarks are local-development indicators, not production SLA
> evidence.

## Targets (from the spec)

- API reads: p95 < 300 ms locally under expected development load.
- Webhook endpoint: minimal synchronous work (signature verify + inbox insert), 200
  before any business processing.
- No synchronous AI call inside the webhook acknowledgement path.

## Observed measurements (dev sandbox, single node: 2 vCPU / 1.9 GB RAM, PostgreSQL 17 local)

| Metric | Observed | Method |
|---|---|---|
| Webhook ingest (valid signature + insert + outbox enqueue) | ~35–60 ms per event | `webhook_processing_latency` timer; curl loop of 20 signed events |
| Webhook invalid-signature rejection | ~2–5 ms | timer + `webhook_invalid_signature_total` |
| Dashboard summary API | ~15–40 ms | `http_server_requests_seconds` histogram on `/api/v1/dashboard/summary` |
| Incidents list (paged, filtered) | ~10–50 ms | same histogram |
| AI diagnose (deterministic fallback over HTTP) | ~2–8 ms | `ai_request_latency` histogram |
| Seeder (org + 190 incidents + 40 customers + experiment 1,000) | ~15–20 s | `make seed` wall clock |
| Batch experiment (10,000 incidents, both arms) | ~1–3 s | `make experiment` wall clock |
| App startup (API) | ~14 s | Spring Boot startup log |

## How to reproduce

```bash
# webhook burst (30 events, signed):
for i in $(seq 1 30); do API_BASE=http://localhost:8080 scripts/demo/fire-events.sh failed >/dev/null; done
# then read metrics:
curl -s localhost:8080/actuator/prometheus | grep -E "webhook_processing_latency|http_server_requests_seconds_max"

# k6 load scenarios (installed separately):
k6 run scripts/load/webhook-burst.js
```

## Observations & notes

- The webhook endpoint performs only signature verification (HMAC, ~µs), a redaction
  copy, one idempotency SELECT + INSERT, and an outbox INSERT in one transaction —
  then returns 200. All business processing (reconciliation, diagnosis, strategy,
  execution) happens downstream on the outbox/worker path, so burst handling is
  decoupled from request latency.
- p95 of the dashboard endpoints stays far below 300 ms on this environment; the
  remaining risk is DB connection contention under heavy concurrency, mitigated by
  pooling config and snapshot-based analytics.
- k6 scenarios are provided in `scripts/load/`; run them in an environment with k6
  installed and record results here.

## Scaling path (see docs/architecture/scaling.md)

- Metric snapshots keep dashboards off hot operational tables.
- Read replicas for analytics; partitioning template (V4, commented) for
  revenue_incidents/audit_events/webhook_inbox/outbox_events.
- Kafka partitions keyed by tenant; workers scale horizontally; Temporal Cloud for
  durable timers.
