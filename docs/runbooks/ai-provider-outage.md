# Runbook: AI Provider Outage

**Severity:** SEV-3 (no revenue impact) · **Owner:** AI on-call

## Symptoms
- `ai_request_failure_rate` high; `ai_request_latency` high.
- Diagnostics show `layer=HYBRID` with fallback evidence more often.

## Design behavior (automatic)
- AI calls have strict timeouts (default 8 s), bounded retries, and a circuit breaker.
- On failure/timeout/malformed output → **deterministic fallback engine**:
  failure taxonomy mapping + heuristic strategy ranking.
- Recovery pipelines never block on AI. Incidents may show `layer=DETERMINISTIC`
  and slightly lower confidence — expected and labeled.

## Triage
1. Check which provider: OpenAI / Gemini / Groq status pages.
2. Check `LLM_PROVIDER` config and API key validity (do not log keys).
3. Verify fallback activity: `ai_fallback_total` metric.

## Mitigation
- Optionally switch `LLM_PROVIDER` to a healthy provider (env change + rolling restart
  of the AI service).
- If all providers are down: leave it — deterministic mode keeps the business running.

## Post-incident
- Replay any incidents you want AI-enriched via the re-diagnose admin action
  (idempotent; updates `incident_diagnoses` only).
