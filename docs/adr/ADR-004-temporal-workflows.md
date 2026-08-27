# ADR-004: Temporal for Durable Workflows

**Status:** Accepted

## Context
Recovery requires delayed execution ("retry tomorrow 18:30"), long timers (promise-to-pay
follow-ups), retries across process restarts, and compensation (cancel action on late
authorization). In-memory `ScheduledExecutorService` timers lose state on restart.

## Decision
Temporal is the durable workflow engine for: recovery execution, promise-to-pay
follow-ups, escalation timers, and reconciliation. Workflows are defined in
`workflow/` (Java SDK), executed by a worker process, and driven by events from Kafka.

For demo/dev without Temporal infra, a **DB-backed scheduler** (`SchedulerService`
polling `next_action_at` on `recovery_actions`/`promises_to_pay`) provides the same
behavior with the same action-execution code path. This fallback is clearly labeled
and production deployments must use Temporal (`TEMPORAL_ENABLED=true`).

## Alternatives considered
- Quartz/Cron + DB state: viable for simple schedules but no workflow history,
  compensation, or timer durability guarantees.
- In-memory timers: rejected — violates the "no important business timers in memory" rule.
- Sagas by hand: rejected — Temporal is the battle-tested implementation of this idea.

## Consequences
- Recovery windows, promise reminders and escalations survive crashes.
- Workflow history doubles as an execution record (plus our audit ledger).
- Additional moving part — mitigated by the demo scheduler fallback.
