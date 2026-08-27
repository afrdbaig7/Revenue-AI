# Event Schemas

This package serves as the canonical registry and documentation for all asynchronous events circulating within the RecoverAI platform.

## Architecture

RecoverAI uses an Outbox pattern for reliable message delivery. Events are initially persisted to the `outbox_events` table in PostgreSQL as part of the same transaction that mutates domain state. The `OutboxPublisher` polls this table and publishes to Kafka (or dispatches inline in demo mode).
