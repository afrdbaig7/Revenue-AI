# RecoverAI: frontend and backend implementation roadmap

Review date: 25 October 2026. This review is based on the current local source, not the README's completion claims. It supplements the existing `project-review-and-improvement-plan.md`, which was already present and untracked when this review started.

## Assessment and verification

RecoverAI has a useful foundation: a modular Spring Boot application, explicit payment/incident states, integer money amounts, an outbox, Temporal workflows, an advisory AI boundary, and an operations dashboard. Keep that architecture. The immediate work is to make authorization, ingestion, external effects, attribution, and browser sessions consistent end to end.

Checks executed during this review:

| Check | Result | What it establishes |
|---|---|---|
| `npm.cmd run typecheck` | Passed | Current TypeScript compiles; casts do not validate server responses |
| `npm.cmd run test` | 10 tests passed in 2 files | Formatting and UI primitive coverage |
| `mvn.cmd test` | 73 tests passed | Existing backend unit/adapter/workflow tests |
| `.venv/Scripts/python.exe -m pytest -q` | 18 tests passed | Existing AI-service tests; cache/deprecation warnings present |
| Docker-backed integration, browser E2E, full production build, live provider calls, visual browser inspection | Not run | Production operation and rendered appearance remain unverified |

Frontend tests initially failed because esbuild could not read parent directories in the sandbox; the approved rerun passed. Offline Maven could not resolve its parent dependency; the approved normal Maven run passed. No application behavior was changed as part of this review.

Priority meanings: P0 = authorization or payment-processing correctness; P1 = required functional reliability and trustworthy reporting; P2 = usability, maintainability, performance, and operational maturity. Static findings below identify code paths and risks; they are not claims that an exploit or production failure was reproduced.

## Backend backlog

### BE-01 — P0: enforce authorization and organization boundaries

Evidence:

- `admin/api/DlqAdminController.java:35`: listing and replaying use global repository methods. An organization ADMIN is not necessarily a platform administrator.
- `system/api/SystemController.java:31`: resolves `orgId` but does not use it for webhook/outbox/DLQ queries. The response includes other organizations' event metadata and global counters.
- `incident/api/IncidentController.java:135`: reprocess, cancel, and promise mutations have no method role checks; the fallback security rule only requires authentication.
- `webhook/application/EventDispatcher.java`: `notes.merchant_id` is accepted after `existsById`, without checking organization ownership. Provider payment/subscription/session lookups also lack account scope.
- `incident/api/IncidentController.java`: detail returns promises for the whole organization instead of the selected incident.

Implementation:

1. Define one operation-to-permission matrix. Apply server checks to every mutation, including checkout/demo ingestion. Keep permission descriptions and actual checks consistent.
2. Introduce transactional, tenant-scoped DLQ services. Require `(orgId, id)` for replay and organization filtering for list/count operations. Return redacted DTOs and audit actor, reason, and result.
3. Separate tenant health from privileged platform telemetry. Replace the current pending count, which can only report 0 or 1, with actual count/oldest-age queries.
4. Resolve the integration/account/merchant from verified webhook routing. Treat provider notes as untrusted references and validate ownership.
5. Audit public APIs and worker entry points for organization checks. Add composite foreign keys where child and parent must share an organization; backfill inconsistent references first.

Acceptance: two organizations x five roles x each protected read/mutation; foreign IDs return 404 with no state change, read-only roles receive 403 on mutations, and an organization's health/DLQ shows only its own data.

### BE-02 — P0: normalize real webhooks and persist only allowed fields

Evidence: `EventDispatcher.handlePaymentEvent` reads `payload.payment` directly; provider examples nest the entity at `payload.payment.entity`. `WebhookIngestionService.redact` removes fields at the wrapper level, then enqueues the original payload. `PaymentService` persists the supplied object in failure details and attempt raw details.

Implementation:

1. Add validated provider DTOs and explicit mapping to canonical payment, subscription, refund, and order events. Reject missing IDs/amounts/statuses rather than defaulting to empty IDs, zero amounts, and CREATED.
2. Keep signature verification on exact raw bytes before parsing. Persist a hash and an allowlisted normalized payload; apply the same policy to inbox, outbox, attempts, audit snapshots, logs, and API responses.
3. Use integration/account-scoped event identities. Insert inbox rows with conflict-safe SQL and enqueue only for the winning insert. Catching a uniqueness error inside an already-aborted database transaction is insufficient.
4. Maintain separate fixtures for canonical internal events and documented provider-shaped payloads. Verify subscription/refund event names and shapes against the provider contract.
5. Update inbox processing states when downstream handling completes or fails; ingestion acceptance and domain processing are different stages.

Acceptance: signed provider-shaped failed/authorized/captured fixtures process in inline and Kafka modes; concurrent duplicates produce one inbox event; sensitive nested fields are absent from all persisted copies.

Reference: [Razorpay payment webhook examples](https://d6xcmfyh68wv8.cloudfront.net/docs/webhooks/payloads/payments/).

### BE-03 — P0: repair outbox retries and consumer delivery

Evidence: `outbox/infrastructure/OutboxEventRepository.java:34` writes FAILED after a retryable failure, while `findPendingDue` selects only PENDING. `OutboxPublisher.poll` publishes a whole batch inside one transaction without a claim/lease. `KafkaRecoveryConsumer` omits `checkout-events`. Checkout ingestion dispatches synchronously and also enqueues the same event.

Implementation:

1. Choose one retry state model. Either retain PENDING with a future attempt time or select an explicit RETRYABLE state; align the partial index with that choice.
2. Claim bounded batches atomically, using row locking with `SKIP LOCKED` or a lease, and commit claims promptly. Publish outside the claiming transaction; record success/retry/dead status in short transactions.
3. Add bounded backoff with jitter, attempt limits, lease expiry, and oldest-pending alerts. Preserve stable event IDs across retries.
4. Add consumer deduplication per handler/event ID in the same transaction as the domain update. Configure explicit retry/dead-letter handling for malformed and permanently failing Kafka records, preserving original coordinates.
5. Add `checkout-events` and remove direct domain dispatch from the checkout request path. Ensure work occurs after durable acceptance.
6. Provide an audited dry-run repair for historical FAILED rows after checking whether external effects may already have occurred.

Acceptance: broker failure retries to eventual delivery or a visible dead letter; two publishers do not concurrently own one claim; redelivery is harmless; poison messages do not silently disappear; checkout behavior matches across dispatch modes.

### BE-04 — P0: fail safely when reconciliation is unavailable

Evidence: `payment/application/PaymentService.java:117` returns the existing payment after a provider exception. `RecoveryOrchestrator.executeAction` may interpret that stale FAILED state as authorization to create another recovery artifact. New captured-first payments cannot pass CREATED -> CAPTURED in the current state machine. Successful webhook handling also builds `Map.of` with a potentially null failure category, causing a runtime exception.

Implementation:

1. Return a typed reconciliation result: verified state with observation time, unavailable, invalid, or ambiguous. Permit an external action only after a fresh, authoritative, eligible observation.
2. Record retryable reconciliation failure without consuming a financial action attempt; schedule a bounded recheck.
3. Make event application tolerant of missing intermediate events and stale observations. Never regress a confirmed capture because an older failed/authorized event arrives later.
4. Separate 'stop further collection' from 'confirmed recovered revenue'. AUTHORIZED and REFUNDED must not automatically contribute captured revenue.
5. Fix null-hostile audit metadata and make payment attempt identity independent from webhook delivery count.

Acceptance: provider timeout produces no order/link/contact; captured-first and reordered deliveries converge; successful payments with no failure category persist and audit successfully; authorization stops recovery without overstating recovered revenue.

### BE-05 — P0/P1: make external effects durable and attributable

Evidence: `RecoveryOrchestrator.executeAction` holds action/incident locks during provider and communication calls. A crash after provider acceptance but before commit can leave a repeatable local action. Failure attempts do not increment the same counter used to construct the next action key. Communications use a random provider idempotency key. A successful link/message leaves the incident EXECUTING; that branch only advances when no action succeeded. Collection matching primarily follows the original payment ID.

Implementation:

1. Split the orchestrator into planning, policy evaluation, action claiming, adapter execution, outcome recording, and settlement reconciliation services. Use short transaction boundaries.
2. Add a durable operation identity, request fingerprint, lease owner/expiry, attempt number, and provider correlation references. Commit intent before the network call.
3. Represent ambiguous outcomes explicitly. Use provider idempotency only where documented; otherwise reconcile by supported references or require operator resolution before creating again. Do not promise exactly-once external effects from a local unique key.
4. Count execution attempts consistently on failure and success. Give each permitted retry a distinct attempt record while retaining the operation identity needed for deduplication.
5. Distinguish action accepted, awaiting customer payment, payment confirmed, blocked, failed, and expired. Add follow-up timers and terminal outcomes for successful artifacts that never lead to payment.
6. Link recovery orders/payment links to the incident, integration account, replacement payment, capture, refunds, and actual intervention costs. Enforce uniqueness of settlement attribution.
7. Publish workflow-start requests through an outbox. Stable Temporal workflow IDs and duplicate-start handling should bridge database/Temporal failures. Enable one authoritative scheduling mode or explicitly test coexistence.

Acceptance: kill a worker after provider acceptance and restart it; no blind duplicate artifact is created. Concurrent executions produce one authorized operation. A replacement capture resolves the intended incident exactly once. Exhausted retries and recovery windows terminate predictably.

### BE-06 — P1: align strategies, policy, communication, and approvals

Evidence: policy groups DELAYED_RETRY under the payment-link switch without checking `allowDelayedRetry`; UPI_RECOVERY has no equivalent link gate. Dispatch defaults to a NOOP that is recorded as success. Email/SMS/WhatsApp strategies all use DEMO_INBOX. Returned blocked/failed communication results are ignored and incident contact counts are still incremented. Approval rejection can leave an incident AWAITING_APPROVAL; approval rows are read without a lock/version check.

Implementation:

1. Create a typed strategy registry with required permissions, capabilities, channels, costs, approval rules, eligibility, and executor. Unknown or unavailable strategies fail closed and cannot rank as executable candidates.
2. Evaluate concrete action parameters: discount amount, intended channel, customer preference, cooldown, recovery deadline, attempt limits, and integration state. Explain each failed gate.
3. Return typed adapter outcomes and update counters only for actual accepted sends or explicitly simulated sends. Use deterministic communication operation keys; enforce customer-level cooldowns under concurrency.
4. Describe order creation accurately: it creates an order and does not itself charge a saved payment method. Restrict unsupported automatic-retry claims.
5. Decide approval with an atomic PENDING-state change/lock and bind it to a proposal version. Revalidate policy, opt-out, expiry, and reconciliation at execution. Reject moves the incident to a documented terminal state; expire pending requests.
6. Keep simulated adapters explicitly labelled. Add real delivery adapters and delivery-status callbacks only after the core lifecycle is reliable.

Acceptance: a strategy x policy matrix covers all catalog entries; disabled capabilities cannot execute; blocked sends do not count as delivered; approve/reject races commit one decision; rejection/expiry leave no stranded incident.

### BE-07 — P1: make integrations use the configured merchant account

Evidence: `IntegrationController` stores encrypted organization credentials, but `ProviderConfig.paymentProvider` selects a singleton from process-wide settings. Listing only active integrations makes disabled rows disappear, preventing re-enabling through the displayed controls.

Implementation: resolve adapters per organization/merchant/integration; decrypt only at call time; include integration identity in cache keys and invalidate on rotation. List active and disabled connections. Add an explicit connection-test endpoint, verified status, webhook URL/setup status, last event time, and audit for configuration/toggle/rotation. Enforce test-key requirements on the server and avoid presenting ciphertext prefixes as useful masked key IDs.

Acceptance: two organizations use different test accounts without crossover; saving credentials changes the account used for that integration; disabling prevents execution and remains reversible through the UI.

### BE-08 — P1: make metrics and audit truthful

Evidence: `DashboardService.summary` counts CLOSED as recovered, calculates incremental revenue with a fixed `0.3985` multiplier, divides recovered revenue by currently open risk, and marks every response synthetic. Summary repeatedly aggregates live incidents despite snapshot infrastructure. `IncidentService.transitionBestEffort` records the previous state after mutating it. The schema has no demonstrated database enforcement of the audit immutability claim.

Implementation:

1. Define a metric dictionary with cohort/date, currency, denominator, attribution, freshness, and exclusion rules. Separate incident recovery rate from amount recovery rate and open risk.
2. Derive confirmed recovered revenue from unique attributed captures; account for refunds, discounts, and delivery/provider costs. Separate recovered and closed-unrecovered outcomes.
3. Report incremental improvement only for an identified valid experiment/comparison. Keep simulation results linked to their run, seed, sample size, and model version; return unavailable when unsupported.
4. Build incremental daily/hourly aggregates by organization/currency and read them for dashboards. Show snapshot freshness. Review query plans and add indexes for measured bottlenecks.
5. Capture prior state before mutation and use a common transition/audit path. Give the application audit writer insert-only database privileges; define controlled retention and privileged maintenance separately.

Acceptance: hand-calculated fixtures containing open, closed, recovered, authorized, refunded, and duplicate events match every displayed total. Rates have defined denominators, currencies are separated, audit transitions are accurate, and application credentials cannot edit audit history.

### BE-09 — P1/P2: harden AI lifecycle and contracts

Evidence: `ai-service/app/main.py:79` creates a provider per call; the constructor creates a fresh circuit breaker and HTTP client. Breaker failures cannot accumulate across requests. Retry count is hardcoded and rank output is `list[dict]`.

Implementation: create/close a shared provider in application lifespan, use a concurrency-safe breaker with controlled half-open probes, honor retry configuration and a total deadline shared with Java, retry only eligible failures, and bound concurrency/request size. Replace nested dictionaries with constrained candidate/date/time/channel models. Validate ranking membership and uniqueness and preserve deterministic authorization. Isolate or authenticate the internal service. Record model/prompt version, fallback reason, latency, token/cost estimates, and compare against held-out deterministic-baseline fixtures.

Acceptance: repeated outages open the breaker across requests; malformed output falls back within the caller deadline; no unsupported strategy reaches execution; clients close on shutdown.

## Frontend backlog

### FE-01 — P1: repair sessions, CSRF, permissions, and cache isolation

Evidence: `src/lib/auth.tsx:62` assumes a flat `/auth/me` response while `AuthController.me` returns `{ user, permissions, orgName }`. The API client redirects immediately on 401 and does not refresh. It reads `ra_csrf`/`X-CSRF-Token`, whereas SecurityConfig uses uncustomized CookieCsrfTokenRepository defaults. Query keys omit organization and logout does not clear the query cache. Navigation and mutation controls are not permission-aware.

Implementation:

1. Define a typed session contract and store `me.user`, permissions, organization name, and loading/error state correctly.
2. Prefer one browser cookie flow; keep API-client Bearer behavior separately defined. Align and eagerly initialize the CSRF cookie/header contract, then renew it after relevant auth transitions.
3. Implement single-flight refresh with one bounded retry, no recursive refresh loop, and server handling for expired access cookies on the refresh endpoint. Coordinate concurrent-tab rotation deliberately.
4. Fix refresh rotation concurrency and durable family revocation; the current revoke-then-throw path needs a transaction test to ensure revocation commits. Recheck disabled users and current membership on refresh.
5. Cancel/clear cached tenant data at auth boundaries and include organization identity in query-key factories. Reset session handlers on provider cleanup.
6. Build `can(permission)` for navigation and actions, but keep server authorization authoritative. Show explicit 403 and session-expired states.

Acceptance: reload preserves correct name/role/org; expiry refreshes once; cookie-authenticated mutations pass CSRF; an invalid CSRF token fails; switching users exposes no previous cached data; read-only roles cannot perform mutations.

Reference: [Spring Security CSRF cookie defaults and SPA integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

### FE-02 — P1: distinguish errors, empty data, and pending changes

Evidence: incidents, approvals, integrations, and policies commonly fall through from request failure to empty-state rendering. Incident reprocess/cancel lack visible mutation-error handling. Integration toggling bypasses a tracked mutation. API errors discard correlation IDs.

Implementation: use shared loading/error/empty/forbidden/not-found/stale-data states; preserve last successful data with a freshness indicator; expose retry actions and support IDs; display mutation progress, conflict/validation errors, and success feedback. Use confirmation and a reason for consequential cancellation/rejection. Keep financial outcome display server-confirmed. Invalidate incident, list, audit, approvals, and summary queries together after relevant actions. Pass abort signals through the API client and React Query.

Acceptance: injected 403/404/409/422/429/500 and network failures render appropriate messages; failures never say 'queue empty'; controls prevent duplicate submissions and users can recover.

### FE-03 — P1/P2: improve operator workflows

Implementation:

1. Incidents: put filters/page/sort in the URL; use failure-category options instead of unvalidated per-keystroke requests; add strategy/amount/date filters backed by validated server parameters; debounce actual text search; show active filters and reset. Preserve filters when returning from detail.
2. Detail: explain current blocker, next action/time, latest reconciliation, confirmed financial outcome, and available operator action first. Paginate audit history instead of silently stopping at 100. Scope promises to the incident and add a structured promise form only when backend semantics are ready.
3. Approvals: implement pagination beyond the first 50, show proposal/policy/expiry context, individual pending states, decision reasons, and decided history. Surface conflicts when another operator decided first.
4. Policies: add client validation matching server ranges, show currency units in merchant-friendly terms, dirty-state protection, version-conflict handling, change summary, active-policy selection, and change audit.
5. Integrations: guide credential setup, connection verification, webhook configuration, and test-event status. Show disabled connections and provider errors accurately.
6. System: show tenant-scoped backlog, oldest age, recent failures, and actual dependency status. Restrict replay to permitted roles with reason and result feedback.

Acceptance: a merchant configures a test integration, receives a test event, finds its incident, understands the policy decision, approves/rejects when permitted, and sees the final outcome and audit without manual database work.

### FE-04 — P2: improve accessibility, presentation, and performance

Evidence: responsive layout and focus-trapping modal primitives already exist; retain them. Some filters and decision-note inputs rely on placeholders, dashboard text is often 10–11px, navigation hardcodes demo organization/mode, and currency helpers format every amount as INR. Landing metrics are static and differ from documented experiment values.

Implementation: label every control, provide keyboard/focus and screen-reader feedback, improve dense-text readability and contrast, add textual chart summaries, test reduced motion and 200% zoom, and verify tables/navigation at narrow widths. Render org/mode from session/integration data. Format currency/timezone from actual data or enforce an explicit INR-only contract. Align marketing examples with one versioned simulation fixture and remove unsupported live/settled/dual-control claims. Consolidate duplicated navigation/status/contracts where they are actually used. Measure bundles and interaction latency before splitting chart code or adding virtualization; current lists already use pagination. Poll only active screens or add SSE after delivery semantics are stable.

Acceptance: keyboard-only critical flows work; controls have accessible names; narrow screens and zoom retain actions; chart values are available as text; real data is never labelled simulated or vice versa. Establish performance budgets from measured production builds.

## Shared engineering and deployment work

- Generate frontend request/response types from an explicit OpenAPI contract instead of maintaining parallel `packages/contracts/api-types.ts` and `src/lib/types.ts` manually. Add schema drift checks and runtime validation at trust boundaries.
- Add server-side pagination/filter validation, positive page sizes, enum errors, request body limits, deterministic sort tie-breakers, and redacted DTOs instead of returning JPA entities and ad hoc maps.
- Fix `Makefile setup` referencing missing `requirements-dev.txt` and `dev` starting only the API. Provide a tested PowerShell path alongside Unix setup. Make migration/seeding commands work on a fresh database.
- Add noninteractive ESLint configuration and explicit lint gates; the web CI job name mentions lint but does not run it. Gate formatting, typecheck, meaningful tests, builds, and dependency/image scans. Pin reproducible installs and review advisories before selecting dependency updates.
- Expand Playwright from navigation smoke checks to real approve/reject, reload, expiry, invalid policy, toggle, error, and role flows. Add container-backed integration tests for the actual orchestrator, not only unique inserts. Existing `WorkerCrashRecoveryIT` does not kill/restart a worker.
- Define API/worker/scheduler process roles explicitly. The worker uses the same application artifact; prevent unintended consumers/jobs on API replicas and duplicate periodic jobs across all replicas.
- Validate Helm ingress and Next proxy together: ingress can route `/api/v1` directly, while direct web-service access still uses a built `http://api:8080` rewrite that differs from release-prefixed service names. A runtime public variable does not establish that routing works.
- Restrict each workload to its own secrets; Helm currently shares the common secret bundle with API, worker, and AI. Validate nonroot UID/filesystem permissions, worker probes, network restrictions, production secret validation, TLS, and migration ownership using built images.
- Add trace propagation through event envelopes, browser support IDs, queue age/lag, ambiguous-action age, reconciliation freshness, DB lock/pool pressure, and fallback metrics. Test backup restore and deploy rollback in staging.

## Delivery plan and dependencies

Estimate: 29–47 engineering days for the core hardening scope, roughly 6–10 focused weeks for one experienced full-stack engineer with Docker/staging available. These are planning ranges, not commitments. Real notification integrations, live-money readiness, provider onboarding, formal security review, and new product features require separate estimates. Frontend work that does not depend on lifecycle changes can proceed alongside backend fixes if additional engineers are available.

| Phase | Effort | Backend deliverables | Frontend deliverables | Exit gate |
|---|---:|---|---|---|
| 0. Baseline | 1–2 days | Reproducible setup/migration/test jobs and honest status | Noninteractive lint/typecheck/test/build | Fresh checkout runs documented commands |
| 1. Authorization | 3–5 days | BE-01, transactional tenant-scoped DLQ, endpoint role checks | Permission model and cache boundary foundations | Two-tenant/five-role integration matrix passes |
| 2. Ingestion and delivery | 4–6 days | BE-02/03, provider DTOs, redaction, retry/claim model, consumer recovery | Accurate processing/error status | Signed fixtures work in inline/Kafka; outages and duplicates converge |
| 3. Safe recovery lifecycle | 6–9 days | BE-04/05, fresh reconciliation, durable operations, settlement mapping, workflow starts | Clear action vs payment outcome, current blocker and next step | Crash-window/concurrent-worker/late-auth tests pass |
| 4. Product control and sessions | 5–8 days | BE-06/07, approval lifecycle, integration account resolution, auth rotation | FE-01/02, validated policies, integration setup, real approval actions | Reload/expiry/CSRF/role and merchant test-account journeys pass |
| 5. Reporting and usability | 4–7 days | BE-08, ledger attribution, snapshots, API DTO/validation | FE-03/04, URL filters, pagination, accessibility, truthful metrics | Known ledger fixtures match dashboard; keyboard/mobile flows pass |
| 6. AI and release validation | 6–10 days | BE-09, process roles, images/Helm, telemetry, backup/rollback | Critical E2E and measured performance | Staging satisfies release scenarios below |

Suggested reviewable PR sequence:

1. Build/setup/lint fixes and recorded baseline.
2. Mutation authorization, tenant-scoped DLQ/system data, ownership validation.
3. Canonical provider DTOs, allowlisted persistence, conflict-safe inbox.
4. Outbox claims/retries, consumer dedupe/dead letters, checkout consistency.
5. Typed reconciliation, captured-first/reordered payment handling, audit fixes.
6. Durable action/communication operations, bounded attempts, ambiguous outcomes.
7. Settlement attribution, waiting states, workflow-start outbox, approval races/expiry.
8. Strategy capability/policy matrix and merchant-specific integration resolution.
9. Session/refresh/CSRF contract, permission-aware UI, tenant-safe query cache.
10. Error/mutation feedback, policy/integration/approval usability.
11. Metric dictionary, capture/refund attribution, aggregate queries, reporting UI.
12. AI lifecycle/schemas/deadlines and fallback evaluation.
13. Accessibility, filter/pagination improvements, generated contracts.
14. Deployment validation, realistic chaos tests, measured load and restore/rollback evidence.

Each correctness change includes its regression test in the same PR. Avoid delaying all validation until the last phase.

## Migration and release gates

Use additive Flyway migrations for operation intents, leases, processing deduplication, provider/account references, attribution records, approval versions, and required indexes. Audit and backfill before adding tenant constraints. Deploy consumers that understand both event versions before changing producers. Preserve operation/event IDs across releases. Repair historical rows with a dry-run report and evidence that replay cannot duplicate an external effect; do not mass-replay ambiguous operations.

Release acceptance scenarios:

1. Signed provider failure -> one incident -> authorized action -> replacement capture -> one attributed recovery.
2. Original payment authorizes just before execution -> collection stops; only subsequent confirmed capture affects recovered revenue.
3. Provider reconciliation outage -> no external action; bounded retries resume after recovery.
4. Duplicate, concurrent, captured-first, and out-of-order events -> stable financial state and attribution.
5. Broker outage, poison record, two publishers, and worker restart -> eventual delivery or an inspectable dead letter.
6. Crash after external acceptance before local commit -> explicit reconciliation of the ambiguous outcome.
7. Opt-out/policy/cooldown changes before execution -> correct blocking and contact counters.
8. Approve/reject race, stale proposal, expiry, and rejected approval -> one decision and consistent incident state.
9. Cross-organization IDs and read-only users -> no disclosure or unauthorized mutation.
10. Browser reload, access expiry, refresh reuse, and logout/login as a different user -> correct identity, committed revocation, CSRF enforcement, and isolated cache.
11. Hand-calculated ledger with captures/refunds/costs/currencies -> exact dashboard agreement.
12. Fresh staging deploy -> working API routing/probes, appropriate secrets, observed performance, successful backup restore and rollback.

Start with authorization plus one provider-shaped payment journey. Defer new providers, voice, additional AI features, and microservice extraction until these release gates pass.
