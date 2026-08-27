# RecoverAI — Security Model

## 1. Authentication

- **Email/password** with BCrypt (cost 12) password hashing.
- **Access token** (JWT, HS256, 15 min TTL) in an `HttpOnly` cookie (`ra_access`).
- **Refresh token** (opaque, 7 days TTL) in an `HttpOnly` cookie (`ra_refresh`);
  stored **hashed** (SHA-256) in `refresh_tokens` with rotation and revocation.
  Each refresh rotates the token (old hash invalidated), detects reuse (revokes family).
- **CSRF**: state-changing requests require the `X-CSRF-Token` header matching the
  `ra_csrf` cookie (SameSite=Lax + CSRF is defense-in-depth).
- Webhook endpoint is authenticated by **Razorpay signature**, not by session.
- **No tokens in localStorage.** Cookies: `HttpOnly`, `SameSite=Lax`, `Secure` in production.

## 2. Authorization (RBAC)

| Role | Capabilities |
|---|---|
| `OWNER` | everything, incl. members and billing |
| `ADMIN` | integrations, policies, users |
| `OPERATOR` | approve / escalate / manage recovery |
| `ANALYST` | analytics, exports, audit read |
| `VIEWER` | read-only dashboards |

Enforced server-side with method security (`@PreAuthorize`) plus tenant scoping on every
query. **Never trust tenant IDs from the client**: the tenant is resolved from the
authenticated principal's membership.

## 3. Tenant isolation

- Every merchant-owned table has `org_id`; all repository queries are tenant-filtered.
- Cross-tenant access returns 404 (not 403) to avoid existence leaks.
- Sign-up/switch-org flows create memberships only through server-side logic.

## 4. Webhook security

1. Raw body is read before any parsing.
2. `X-Razorpay-Signature` verified as HMAC-SHA256 of the raw body with the integration's
   webhook secret; invalid signatures rejected with 401 and never parsed/processed.
3. `(provider, provider_event_id)` unique constraint → idempotent ingestion.
4. Payloads are stored redacted (no card numbers, no full PANs, no tokens).
5. Webhook handler performs zero slow work (no AI, no provider calls) before 200.

## 5. LLM / AI safety

- **AI is advisory only** — no write path to financial state (see ADR-006).
- All AI endpoints validate output against strict Pydantic schemas; malformed output
  falls back to the deterministic engine.
- Secrets, tokens, full credentials and card data are never sent to the LLM.
- System prompts are immutable application constants; merchant/customer text is
  constrained to data fields and treated as untrusted (prompt-injection resistance,
  output-schema enforcement).
- Bounded timeouts, retries, and a circuit breaker; AI outage never blocks recovery.

## 6. Secret management

- `.env` is git-ignored; `.env.example` holds placeholders only.
- Razorpay key/secret and webhook secrets are **encrypted at rest** (AES-GCM with a
  master key from `ENCRYPTION_KEY`) before storage in `merchant_integrations`.
- Secrets never appear in logs, responses, or LLM prompts.

## 7. Transport & hardening

- HTTPS assumed in production; secure headers (HSTS, X-Content-Type-Options, frame
  denial, CSP) configured in the ingress manifest.
- CORS allowlist (frontend origin only).
- Request size limits, input validation (Jakarta Validation), output encoding (React
  escapes by default), SQL injection prevented via JPA bind parameters.
- Rate limiting: login (per-IP + per-user), webhook (per-IP), AI (tenant quota),
  notifications (tenant quota), provider calls (bounded backoff on 429).
- SSRF protection: provider HTTP clients restrict to allowlisted hosts
  (Razorpay API base) when not in mock mode.

## 8. Auditability

`audit_events` is append-only: no update/delete API, DB user has no UPDATE/DELETE
privileges on the table in production (documented grant script). Snapshots of decision
inputs/outputs enable full decision replay.

## 9. Data privacy

- Synthetic customer data only in dev/demo (generated, no real PII).
- Minimum necessary data stored; retention architecture and future anonymization
  support documented in `docs/security/privacy.md`.
- Logs redact emails/phones by default (`[REDACTED]`) outside demo mode.

## 10. Known limitations (v1)

- Email/SMS/WhatsApp adapters are interface-complete; demo provider renders to an
  in-app inbox (never mislabeled as real sends).
- No SSO yet (extension points designed: `AuthenticationProvider` seam).
- No per-key API-rate-limit dashboard (Redis counters exist; UI pending).
