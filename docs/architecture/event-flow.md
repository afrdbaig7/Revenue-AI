# RecoverAI — Event Flow & Event Catalog

## 1. Webhook ingestion (mission-critical path)

```mermaid
sequenceDiagram
    participant RZ as Razorpay (test mode)
    participant API as API /webhooks/razorpay
    participant PG as PostgreSQL
    participant O as Outbox
    participant BUS as Kafka/Redpanda
    participant W as Worker
    participant AI as AI service
    participant T as Temporal
    participant ACT as Action executor

    RZ->>API: POST raw body + X-Razorpay-Signature
    API->>API: read RAW body (no parse yet)
    API->>API: verify HMAC-SHA256 signature (per integration secret)
    alt invalid signature
        API-->>RZ: 401, never parsed/trusted
    end
    API->>PG: upsert webhook_inbox (provider, provider_event_id UNIQUE)
    API->>PG: insert outbox_event (same transaction)
    API-->>RZ: 200 {received:true} (fast, no AI, no provider calls)
    O->>BUS: publish payment-events
    BUS->>W: consume
    W->>PG: reconcile payment state (tolerates out-of-order)
    W->>PG: create/update revenue incident
    W->>AI: diagnose (async, bounded timeout)
    AI-->>W: structured diagnosis (or deterministic fallback)
    W->>PG: persist diagnosis + decision
    W->>PG: policy evaluation
    alt needs approval
        W->>T: await approval signal
    end
    W->>T: schedule recovery workflow (delayed execution)
    T->>ACT: execute at scheduled time
    ACT->>RZ: provider call (payment link / retry)
    RZ-->>ACT: result
    ACT->>PG: record action result + audit
    RZ-->>API: follow-up webhook (payment.authorized / captured)
    API->>BUS: payment-events
    W->>PG: reconcile → incident RECOVERED / LATE_AUTHORIZED handling
```

## 2. Event catalog

| Topic | Producer | Payload shape (key fields) | Consumers |
|---|---|---|---|
| `payment-events` | outbox (webhook/processor) | event_id, event_type, provider_payment_id, amount_minor, currency, status, failure_code, occurred_at | Recovery Worker, Metrics Worker |
| `subscription-events` | outbox | event_id, event_type, provider_subscription_id, status, phase, failure_code | Recovery Worker, Metrics Worker |
| `recovery-incidents` | API domain | incident_id, tenant_id, status, failure_category, amount_minor | Audit Worker, Metrics Worker, Analytics |
| `recovery-actions` | API domain / worker | action_id, incident_id, strategy, status, scheduled_for | Audit Worker, Metrics Worker |
| `recovery-results` | worker | incident_id, action_id, outcome, recovered_amount_minor | Metrics Worker, Dashboard |
| `audit-events` | API domain (outbox) | event_type, actor, entity, before/after snapshots | Audit Worker (fan-out to search/archive) |
| `notification-events` | worker | communication_id, channel, status, simulated | Metrics Worker |
| `dead-letter-events` | any consumer | original_topic, key, payload, error, attempts | DLQ monitor, System Health |

### Event shape (canonical)

```json
{
  "schemaVersion": 1,
  "eventId": "evt_9f8d...",
  "eventType": "PAYMENT_FAILED",
  "occurredAt": "2026-08-22T12:00:00Z",
  "tenantId": "0190f1...",
  "correlationId": "8f2c...",
  "payload": { "...": "..." }
}
```

### Out-of-order tolerance

Webhook delivery order is **never assumed**. All handlers are designed as
*reconciliation*: they apply state transitions that are valid from any current state
(e.g. `FAILED → AUTHORIZED` is legal and triggers late-authorization handling;
`CAPTURED` is terminal for collection purposes). Duplicates are deduplicated by the
`webhook_inbox (provider, provider_event_id)` unique constraint and by idempotency keys.

## 3. Transactional outbox

1. Business transaction updates state and inserts `outbox_events` **in the same DB transaction**.
2. `OutboxPublisher` polls unpublished rows (or emits change events) and publishes to Kafka.
3. On success: row marked `PUBLISHED`. On bounded failure: row marked `DEAD` → DLQ with replay.
4. In `EVENT_DISPATCH_MODE=inline` (demo/dev), the publisher invokes the same consumer
   handlers in-process after commit — identical handler code, no broker required.

## 4. Dead-letter queue

- Bounded retries (5) with exponential backoff + jitter, then `DEAD`.
- `dead-letter-events` topic records original topic/key/payload/error/attempts.
- System Health screen shows DLQ count and allows **idempotent replay**.
