# ADR-002: PostgreSQL as the System of Record

**Status:** Accepted

## Context
Financial state, workflow state, idempotency keys, audit ledger and policy configuration
all need strong consistency, referential integrity and crash safety. Redis/Kafka are
fast but not durable enough for money; a warehouse is premature.

## Decision
PostgreSQL 16+ is the authoritative store for **all** business state. Redis is used only
for ephemeral coordination (rate limits, distributed locks, short-lived cache, session
ephemera). Kafka carries events but is a **projection** of outboxed facts, never the
source of truth. Money stored as `BIGINT` minor units (see ADR-007). IDs are UUIDs
(`gen_random_uuid()`); UUIDv7 will be adopted when Postgres 18 support matures
(time-ordered UUIDs reduce index bloat and enable sharding later).

## Alternatives considered
- MySQL: fine, but Postgres JSONB, partial indexes, and CHECK/EXCLUDE power suit
  flexible financial payloads and state machines.
- DynamoDB/Cassandra: rejected — relational invariants (unique idempotency keys,
  FK integrity) are load-bearing here.
- Event sourcing: rejected for v1 — audit ledger already gives full history without
  event-sourcing's read-model complexity.

## Consequences
- Single writer per entity (optimistic locking) — safe and simple.
- Analytics read from snapshot tables to protect the operational store.
- Scaling path: read replicas → partitioning → warehouse (see scaling.md).
