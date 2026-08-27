# Runbook: Provider (Razorpay) Outage

**Severity:** SEV-1 (if affecting reconciliations) / SEV-2 · **Owner:** Platform on-call

## Symptoms
- `razorpay_request_latency` elevated; `provider_error_rate` > threshold (5xx/429).
- Recovery actions failing with `PROVIDER` category; incidents stuck `EXECUTING`.

## Design behavior (automatic)
- All provider calls have timeouts + bounded exponential backoff + jitter.
- Circuit breaker opens after N consecutive failures → actions fail fast with
  `RETRYABLE_FAILURE`, no further provider load.
- Webhook ingestion is unaffected (signature verify + inbox only).
- Incidents remain `EXECUTING`/`RETRYABLE_FAILURE`; nothing is lost.

## Triage
1. Confirm outage scope: `curl -s -o /dev/null -w "%{http_code}" https://api.razorpay.com/v1/payments` (or status.razorpay.com).
2. Check circuit state in `/actuator/health` and metrics `circuit_breaker_state`.

## Mitigation
- Do **not** restart workers aggressively (they are doing the right thing: backing off).
- If the outage is long: consider pausing recovery scheduling via policy
  (`recoveryWindowHours` / manual pause flag in Policy Config) to avoid piling
  pending actions that will all fire at once after recovery.
- Reconciliations resume automatically when the provider returns.

## Post-incident
- After provider recovery, pending `SCHEDULED` actions fire; `EXECUTING` actions are
  re-driven by the reconciliation job (idempotent by `idempotency_key`).
- Verify no double collection: check `payments.status` vs `recovery_actions.status`
  and the `LATE_AUTHORIZATION_RECEIVED` audit events.
