# ADR-003: Kafka-Compatible Event Backbone

**Status:** Accepted

## Context
Workers must process payment events asynchronously, decoupled from the webhook
acknowledgement path; multiple consumers (recovery, audit, metrics) need the same events.

## Decision
Use Kafka-compatible infrastructure — **Redpanda locally** (single binary, no ZooKeeper,
low memory) and Kafka/Redpanda Cloud in production. Topics: `payment-events`,
`subscription-events`, `recovery-incidents`, `recovery-actions`, `recovery-results`,
`audit-events`, `notification-events`, `dead-letter-events`.

Constraints:
- Events are only published **via the transactional outbox** (ADR-005).
- Kafka is never a source of truth; consumers reconcile against PostgreSQL.
- Synchronous request-response stays on REST; Kafka is not used for control-plane calls.

## Alternatives considered
- RabbitMQ: weaker replay/partitioning story for event streams and consumer groups.
- NATS/Redis streams: simpler but weaker operational ecosystem and replay semantics.
- No broker (direct worker DB polling): rejected — couples webhook path to workers and
  makes multi-consumer fan-out awkward.

## Consequences
- Event replay is possible (reset consumer groups, DLQ replay).
- Partition key `tenant_id` preserves per-incident ordering.
- `EVENT_DISPATCH_MODE=inline` exists for demo/dev without a broker — it invokes the
  same handlers in-process (documented divergence).
