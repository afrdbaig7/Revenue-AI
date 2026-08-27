# Runbook: Database Degradation

**Severity:** SEV-1 if degraded > 15 min · **Owner:** Platform on-call

## Symptoms
- `database_pool_usage` near max; slow queries; `http_request_duration` p95 rising.
- `pg_stat_activity` shows long `state=active` queries.

## Triage
1. Slow query log / pg_stat_statements: top 5 by total time.
2. Lock waits: `SELECT * FROM pg_locks WHERE NOT granted;`
3. Bloat: `SELECT * FROM pgstattuple('revenue_incidents');`

## Common causes & fixes
| Cause | Fix |
|---|---|
| Missing index (new query pattern) | Add index via Flyway migration (see data-model.md index list) |
| Analytics scanning hot tables | Ensure dashboard uses `metric_snapshots`; stop ad-hoc scans |
| Lock contention on incident rows | Optimistic locking handles contention; check for long-running txn (webhook handler) holding locks |
| Connection exhaustion | Raise pool max only after confirming DB CPU/memory headroom |
| Bloat | `VACUUM (ANALYZE)` / autovacuum tuning; scheduled maintenance window |

## Mitigation (if degraded)
- If the DB is unreachable: API fails fast (readiness = false → LB drains);
  webhook endpoint returns 503 → Razorpay retries delivery (their retry policy).
  No data loss: events wait at the provider; once DB recovers, ingestion resumes.
- Scale: read replica for analytics (read-only traffic only).

## Recovery & verification
- Verify Flyway runs cleanly (`mvn flyway:migrate` / `make migrate`).
- Verify webhook lag drains: `webhook_inbox` oldest `received_at` recency.
- Confirm reconciliation job caught up: no incidents stuck `RECONCILING` > 1 h.
