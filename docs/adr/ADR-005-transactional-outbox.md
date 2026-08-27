# ADR-005: Transactional Outbox

**Status:** Accepted

## Context
"DB commit succeeds but event publish fails" would silently desynchronize workflow
state (incident created, no event emitted → nobody acts on it). Conversely, publishing
before commit risks emitting events for state that rolled back.

## Decision
The outbox pattern: business mutation and `outbox_events` insert happen **in the same
DB transaction**. A publisher (`OutboxPublisher`) polls pending rows (or receives change
hints) and publishes to Kafka; success marks `PUBLISHED`, bounded retries then `DEAD`
(DLQ with replay). `OutboxEvent` rows carry aggregate type/id, event type, payload and
correlation ID.

## Alternatives considered
- CDC (Debezium): elegant but another heavy system; unnecessary at this scale.
- Publish-after-commit in application code: the exact failure mode we reject.
- Async local queue: lost events on crash.

## Consequences
- Exactly-once-ish event emission (at-least-once + idempotent consumers).
- Slight latency (poll interval ≤ 1 s in demo; can be reduced with LISTEN/NOTIFY).
- DLQ + replay tooling gives operators control.
