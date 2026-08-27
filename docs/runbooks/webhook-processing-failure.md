# Runbook: Webhook Processing Failure

**Severity:** SEV-2 · **Owner:** Platform on-call

## Symptoms
- `webhook_processing_latency` p95 rising; `webhook_*_total` flat while Razorpay
  dashboard shows deliveries.
- `webhook_inbox.processing_status` rows stuck in `RECEIVED`/`RETRYING` (SQL below).
- DLQ count increasing for `payment-events`.

## Triage (5 min)
1. Check Grafana: webhook latency, error rate, consumer lag.
2. Check worker logs for exceptions (structured JSON: `event=WEBHOOK_PROCESSING_FAILED`).
3. Identify the failure category: `TRANSIENT` (DB connection, provider timeout) vs
   `PERMANENT` (schema violation, malformed payload).

## Mitigation
- **Transient**: the consumer retries with bounded backoff automatically; verify DB
  connectivity and pool saturation (`database_pool_usage` metric).
- **Permanent**: events go to DLQ — do not loop. Investigate the offending payload
  (redacted) and fix code/schema, then replay (see `dlq-replay.md`).

## Verification queries
```sql
SELECT processing_status, count(*) FROM webhook_inbox
WHERE received_at > now() - interval '1 hour' GROUP BY 1;

SELECT * FROM outbox_events
WHERE status IN ('PENDING','FAILED') AND next_attempt_at < now() ORDER BY created_at;
```

## Recovery
- If the worker crashed mid-batch: Kafka consumer groups rebalance; at-least-once
  delivery + idempotent handlers make replay safe.
- If outbox rows are PENDING and publisher stalled: restart publisher; it resumes
  from `outbox_events` (source of truth).

## Post-incident
- Document root cause in the incident report; add regression test if a code bug.
