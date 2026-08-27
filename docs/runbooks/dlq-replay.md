# Runbook: DLQ Replay

**Severity:** SEV-3 · **Owner:** Platform on-call

## Context
Events that fail after bounded retries land in the `dead-letter-events` topic and are
visible in **System Health → Dead Letter Queue** with: event, failure reason, attempts,
first/latest failure.

## Safe replay rules
- Replay is **idempotent**: every handler dedupes via `webhook_inbox(provider,
  provider_event_id)`, idempotency keys, and state-machine CAS. Replaying an already
  processed event is a no-op.
- Replay order: oldest first; only replay `PERMANENT`-category events **after** the
  root cause is fixed, otherwise they will fail again.

## Procedure
1. System Health → DLQ → filter by topic/error.
2. Select events → **Replay** (or API: `POST /api/v1/admin/dlq/replay`).
3. Monitor `webhook_inbox.processing_status` and incident creation counters.

## Verification
```sql
-- no stuck rows
SELECT status, count(*) FROM outbox_events GROUP BY 1;
SELECT count(*) FROM webhook_inbox WHERE processing_status = 'RECEIVED'
  AND received_at < now() - interval '2 hours';
```

## Notes
- Do **not** replay into a live payment if the incident already recovered —
  idempotency + reconciliation make this safe, but verify with
  `SELECT status FROM revenue_incidents WHERE id = '<incident>'`.
