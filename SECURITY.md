# Security Policy

## Reporting a vulnerability

**Do not open a public issue for security findings.** Report privately to the
maintainers (repo security advisory flow, or `security@recoverai.dev` placeholder —
replace with a real address before production use). Include: affected component,
reproduction steps, impact, suggested fix. Expect acknowledgement within 48 h and a
coordinated disclosure timeline.

## Secret-handling policy

- `.env` is git-ignored; only `.env.example` with placeholders is committed.
- Razorpay key/secret/webhook secrets are encrypted at rest (AES-GCM) via
  `ENCRYPTION_KEY`; decrypted only in the integration service at call time.
- Secrets must never appear in: logs (structured or not), responses, error payloads,
  LLM prompts, or browser bundles.
- Production secrets come from a secrets manager / k8s Secrets (see
  `infrastructure/helm`); rotate webhook secrets without downtime (multiple
  active secrets supported per integration).

## Synthetic-data policy

All demo/dev customer data is generated and synthetic. No real card numbers, bank
accounts, or PII are required anywhere. Demo runs never contact live payment networks.
Anything simulated is labeled **SIMULATED / SYNTHETIC TEST-MODE** in the UI and in
exported reports.

## Authentication

- BCrypt (cost 12) password hashing; JWT access tokens (15 min) and opaque refresh
  tokens (7 days) with rotation + reuse detection, delivered via `HttpOnly`,
  `SameSite=Lax`, `Secure`-in-production cookies. No tokens in localStorage.
- CSRF tokens required for state-changing requests (except signature-authenticated
  webhooks). Brute-force protection via per-user/per-IP rate limits and lockout.

## Authorization

RBAC roles OWNER/ADMIN/OPERATOR/ANALYST/VIEWER enforced server-side with method
security; every query is tenant-scoped from the authenticated principal. Client-supplied
tenant IDs are never trusted. Cross-tenant reads return 404.

## Webhook security

Raw-body HMAC-SHA256 verification against the integration's webhook secret **before**
parsing; invalid signatures are rejected (401) and counted
(`webhook_invalid_signature_total`). Duplicates are deduped by
`(provider, provider_event_id)`. Payloads are stored redacted.

## LLM safety

- AI is advisory only; it has no write path to financial state.
- Strict output schemas; deterministic fallback; bounded timeouts; circuit breaker.
- System prompts are fixed constants; merchant/customer content is treated as
  untrusted data (prompt-injection resistance). Secrets/card data never enter prompts.
- Model metadata (provider, model, prompt version) recorded per decision.

## Known limitations

- Test-mode payment processing only (Razorpay TEST MODE).
- No SSO yet (extension points designed).
- Demo notification provider simulates channels; simulated sends are labeled.
- Audit ledger is append-only by application rule; production DB should also revoke
  UPDATE/DELETE on `audit_events` from the app role (grant script provided in
  `infrastructure/docker/postgres/grants.sql`).

## Scope

This policy covers the `recoverai` monorepo (apps/api, apps/ai-service, apps/web,
apps/worker, infrastructure). Third-party dependencies are scanned in CI
(OWASP Dependency-Check / `npm audit` / `pip-audit`).
