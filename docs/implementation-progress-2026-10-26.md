# Implementation progress — 26 October 2026

This records the current implementation pass against the review and roadmap. The full roadmap is not complete, and production readiness has not been established.

## Implemented

- Tenant-scoped administration, provider lookups and relationship constraints; role checks for recovery mutations; production secret checks; cookie/CSRF and refresh rotation fixes.
- Short outbox claim transactions, expiring delivery leases, transactional event deduplication, bounded Kafka retries and durable Temporal start requests.
- Verified webhook normalization, integration-specific webhook routing, payment state ordering, reconciliation checks and tenant-specific provider credentials.
- Recovery intent committed before external execution, execution leases, ambiguous outcome handling, supported-strategy gates and an awaiting-payment state.
- Capture/refund settlement ledger, duplicate-capture detection, ledger-based recovered metrics and append-only audit enforcement.
- Frontend session restoration, tenant query keys and cache cleanup, recoverable error views, permission-aware mutation controls, approval pagination, policy validation/version conflicts, incident URL filters and accessible mobile navigation.
- AI provider reuse, bounded concurrency/retries, circuit breaker, response validation, deterministic fallback and internal authentication.
- Runtime web API proxy, container ownership fixes, separated deployment secrets/process roles, network policies, CI checks and a Windows development helper.
- Follow-up completion: incident date boundaries in IST and allowlisted stable sorting; strategy controls; paginated detail audit; explicit promise dates, channels and bounded amounts; recorded cancellation reasons; policy change review with atomic before/after audit snapshots; expandable chart data tables; shared contract types replacing the duplicate frontend definitions.

## Local validation

| Check | Result |
|---|---|
| Backend unit/service/security tests | 95 passed |
| Frontend unit tests | 16 passed |
| AI tests | 25 passed |
| AI Ruff checks | Passed |
| Frontend type checking and lint | Passed |
| Next.js production build | Passed |
| Browser session/error/permission/approval/filter/promise/cancellation/policy journeys | 10 passed using installed Microsoft Edge |
| Git whitespace check | Passed |

Browser journeys use mocked API responses. They validate browser behavior, not a deployed end-to-end payment flow. The PostgreSQL integration tests compile but were not run locally because Docker is unavailable. Helm lint, container boot, live provider calls, staging deployment, load tests and restore drills were not run.

## Next release checks

1. Back up the target database and prove a restore in an isolated environment before migration.
2. Run `mvn verify` from `apps/api` with Docker available. Inspect the Testcontainers results, including durable delivery and settlement tests.
3. Run `helm lint infrastructure/helm/recoverai`, render the chart with deployment values, and review secrets, role separation, probes and network policy selectors. Build and boot every image.
4. Review and apply Flyway V4–V7 in staging. V6 introduces `NOT VALID` tenant constraints: audit historical rows and validate each constraint after cleanup. New writes are constrained immediately.
5. Review historical failed outbox records before replay; they are deliberately not mass-replayed. Existing recovered incidents need provider-verified settlement backfill before historical financial totals can be considered complete.
6. Configure separate strong encryption/signing keys, secure cookies, allowed origins and a shared internal AI key. Use the Spring `production` profile and AI `PRODUCTION_MODE` in production. Keep Razorpay credentials in test mode for validation.
7. Run complete staging flows: signed webhook, duplicate/reordered delivery, capture/refund, authorization boundaries, expired session refresh, provider timeout, worker restart and lease expiry. Reconcile reported amounts against provider records.
8. Exercise queue backlog, dependency outages, restart recovery, backup restore and application rollback. Schema rollback requires an explicit database recovery plan; do not remove migration records or assume old binaries support new states.

## Remaining roadmap work

- OpenAPI-generated contracts and automated verification against backend schemas. The frontend now imports one shared contract file; this file is still maintained manually.
- Further chart accessibility review across the strategy and experiment pages; the overview now includes expandable tables. Policy change history now exposes recorded before/after snapshots in the audit log; existing historical changes cannot be reconstructed automatically.
- Real notification adapters and a complete operator workflow to resolve ambiguous provider outcomes.
- Further orchestration decomposition and review of external calls that still occur inside other transactional paths.
- Model evaluation datasets, quality/cost reporting and complete decision metadata.
- Historical settlement backfill, validated tenant constraints, realistic load/restart/chaos tests and production operational drills.

## Local commands

From the repository root on Windows, `./scripts/dev.ps1` supports `-Command Setup`, `Infra`, `Migrate`, `Seed`, `Dev` and `Test`. Infrastructure requires Docker; the helper's full infrastructure startup has not been validated on this machine.

For browser checks, build the web app and run `npm run e2e:ui` from `apps/web`. Set `PLAYWRIGHT_CHANNEL=msedge` in PowerShell when using installed Edge instead of Playwright Chromium. These checks use port 3100.

Set `API_PROXY_TARGET` on the web process at runtime. Set `PROCESS_ROLE=api` and `WORKER_ENABLED=false` on API-only deployments; worker deployments use `PROCESS_ROLE=worker` and `WORKER_ENABLED=true`. The worker role denies application API routes. Follow the Helm values and rendered resources for the full configuration.
