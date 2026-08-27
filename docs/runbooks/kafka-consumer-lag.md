# Runbook: Kafka Consumer Lag

**Severity:** SEV-2 if lag hours+ · **Owner:** Platform on-call

## Symptoms
- `kafka_consumer_lag` (sum by group) growing; Grafana alert `RecoveryLagHigh`.

## Causes
- Worker crash/restart loop, slow consumer (provider call latency), partition skew
  (one large tenant), broker issue.

## Triage
1. `kafka_consumer_lag` by group and partition (Grafana).
2. Worker logs: exceptions, `max.poll.interval` violations.
3. `kafka_consumer_paused_total` (broker backpressure on 429s).

## Mitigation
- **Fix the root cause first** — scaling consumers while they error just parallelizes failure.
- Scale out worker replicas (HPA on lag) once healthy.
- For partition skew: repartition the topic (keyed by tenant) per scaling.md.
- If lag is hours and actions' execution windows are tight, note that `SCHEDULED`
  actions are persisted in Postgres and executed by the **scheduler**, not the Kafka
  consumer — so lag does not silently lose revenue; it only delays incident creation.

## Post-incident
- Replay missed events idempotently if the group offset reset was required.
