# RecoverAI — Scaling

## Target scale

Design target: millions of payment events/day, bursty webhook delivery, many concurrent
merchants. Local benchmark targets (documented, measured — see `docs/performance.md`):
API reads p95 < 300 ms under dev load; webhook endpoint does minimal synchronous work.

## Current topology

- **Modular monolith** (API) owns transactional state — the write path is a single DB
  transaction per operation, which is the cheapest correct design at this stage.
- **Stateless workers** (Kafka consumers) scale horizontally for event processing.
- **AI service** is stateless and horizontally scalable; it holds no state.
- **Temporal** owns durable timers/workflows; worker fleet scales independently.

## Scaling levers (in order of cost)

1. **Vertical**: larger DB instance; JVM heap tuning; connection pool sizing.
2. **Read scaling**: metric snapshots already keep dashboards off hot operational tables;
   add read replicas for analytics queries; materialize heavy aggregations (V3 migrations).
3. **Partitioning**: `revenue_incidents`, `audit_events`, `webhook_inbox`, `outbox_events`
   partition by `created_at` (range) or `org_id` (list) — migration path documented in
   `V4__partitioning_template.sql` (commented, opt-in).
4. **Horizontal workers**: increase Kafka partitions per topic (8 default) + consumer
   group scale-out; key by `tenant_id` to preserve per-incident ordering.
5. **Temporal**: managed Temporal Cloud; workflow-per-incident isolation.
6. **Cache**: Redis for idempotency short-circuit, rate limits, hot reads (incident
   lookup by provider_payment_id), distributed locks for cross-instance coordination.
7. **Warehouse (future)**: ClickHouse/BigQuery for long-range analytics; operational
   dashboards remain on Postgres snapshots. Not added prematurely (ADR-002 consequence).

## Kafka topic sizing

| Topic | Partitions | Retention | Notes |
|---|---|---|---|
| payment-events | 8 | 7d | keyed by tenant_id |
| subscription-events | 8 | 7d | keyed by tenant_id |
| recovery-incidents | 4 | 30d | |
| recovery-actions | 4 | 30d | |
| recovery-results | 4 | 30d | |
| audit-events | 8 | 90d | |
| notification-events | 2 | 7d | |
| dead-letter-events | 2 | 30d | DLQ monitoring |

## Backpressure & burst handling

- Webhook endpoint: minimal sync work (signature verify + inbox insert), responds 200
  before any business processing; bursts are absorbed by the inbox table + consumers.
- Kafka consumers: bounded batch sizes, `max.poll.interval` tuned, pause-on-error with
  DLQ after bounded retries.
- Provider calls: client-side rate limiter + bounded exponential backoff on 429.

## Production deployment

- Kubernetes manifests + Helm chart in `infrastructure/kubernetes` + `infrastructure/helm`.
- **Managed services recommended**: Postgres (RDS/Cloud SQL/Neon), Redis (ElastiCache/Upstash),
  Redpanda Cloud / Confluent, Temporal Cloud, S3-compatible storage.
- HPA on workers (Kafka lag) and AI service (CPU + RPS); PDBs; readiness/liveness probes;
  pod disruption budgets; Ingress with TLS termination and secure headers.
- Multi-AZ DB, automated backups (PITR), IaC via Terraform (`infrastructure/terraform`).

## Performance measurement

- k6 scenarios in `scripts/load/` (webhook burst, dashboard queries, incident ingestion).
- Prometheus histograms: `http_request_duration`, `webhook_processing_latency`,
  `kafka_consumer_lag`, `ai_request_latency`, `razorpay_request_latency`.
- Results are recorded in `docs/performance.md` as they are measured — never fabricated.
