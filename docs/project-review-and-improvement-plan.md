# RecoverAI: project review and implementation plan

Reviewed: 25 October 2026.

## Assessment

RecoverAI has a substantial demo implementation: a Next.js dashboard, a Spring Boot modular monolith, a separate advisory AI service, PostgreSQL migrations, outbox processing, Temporal workflows, and deployment manifests. Keep this architecture while correcting the core execution paths. Additional services and providers should follow demonstrated correctness of the existing payment recovery loop.

The implementation-status document overstates readiness. Its statement that all remaining gaps are environment-bound is contradicted by the source issues below. Passing unit tests do not establish correctness of webhook processing, tenant isolation, external side effects, or restart recovery.

### Verification performed

- Backend: `mvn.cmd test -q` passed; Surefire reports contain 73 tests, zero failures/errors/skips.
- AI service: `.venv/Scripts/python.exe -m pytest -q -p no:cacheprovider` passed all 18 tests.
- Web: `npm.cmd run test` passed 10 tests across two files.
- Web: `npm.cmd run typecheck` completed without reported type errors.
- Web: `npm.cmd run lint` did not complete successfully; it opens the ESLint configuration prompt.
- Docker was not available on PATH. Container integration tests, live Kafka/Temporal, Kubernetes deployment, load tests, and a running browser walkthrough were not verified.
- Findings below are source-confirmed unless explicitly described as a risk requiring integration validation. Production code was not changed during this review.

## Findings in priority order

### P0: tenant boundaries are incomplete

Evidence: `apps/api/src/main/java/com/recoverai/admin/api/DlqAdminController.java`, `system/api/SystemController.java`, and `webhook/application/EventDispatcher.java`.

DLQ list, single replay, and replay-all use global queries without resolving the authenticated organization. An organization administrator can query events belonging to other organizations; replay also lacks an organization predicate. The system health endpoint resolves an organization but returns global event counts and dead-event details. Webhook merchant resolution accepts a merchant UUID after `existsById`, without checking that it belongs to the event's organization.

Payment, subscription, and checkout provider-ID lookups also need consistent organization/integration ownership checks. Their current global lookups can return entities without validating the event's organization.

Implementation:

1. Move tenant-facing DLQ operations into a transactional application service.
2. Resolve the organization from `CurrentUser`; require it in every query and update predicate.
3. Return tenant-scoped DTOs, with bounded pagination and redacted error/payload fields.
4. Verify merchant, customer, payment, subscription, and checkout ownership at domain boundaries.
5. Add compound tenant/reference constraints where relationships must remain within an organization. Audit existing rows before validating new constraints.
6. Keep platform-wide diagnostics behind a separate platform operations permission boundary.

Acceptance: with organizations A and B, A's administrator cannot list, count, inspect, or replay B's events; foreign UUID references return 404. Successful replay commits and records its actor.

### P0: real webhook payloads are parsed at the wrong level

Evidence: `webhook/application/EventDispatcher.java:71` reads `payload.payment` as the payment. Razorpay's documented shape is `payload.payment.entity`. The subscription handler has the equivalent nesting problem. Redaction also targets fields on the wrapper rather than the nested entity.

Source: [Razorpay payments webhook examples](https://razorpay.com/docs/webhooks/payments/).

Implementation:

1. Introduce provider-specific input DTOs and a normalizer that extracts the nested entities.
2. Validate event type, provider identifiers, amount, currency, and authenticated integration ownership before domain persistence.
3. Persist only an allowlisted normalized payload. Currently the inbox receives a redacted copy, but the outbox receives the original `event.payload`; payment details also retain raw input.
4. Use sanitized fixtures with the documented nesting, rather than only simplified demo events.
5. Correct payment transition handling: a first observed captured payment currently starts at CREATED and is rejected by CREATED-to-CAPTURED validation. Define how stale events are ignored or reconciled instead of throwing indefinitely.
6. Replace the nullable `failureCategory` value inside `PaymentService`'s `Map.of` audit snapshot. A payment without a failure category can trigger a null-pointer failure there.

Acceptance: signed failed/authorized/captured fixtures traverse inbox, outbox, payment, incident, and audit successfully. First-event capture, reordered delivery, duplicates, invalid fields, and sensitive nested fields are covered.

### P0: outbox retries stop after the first failure

Evidence: `outbox/infrastructure/OutboxEventRepository.java`. The poll query selects only PENDING; `markFailed` changes retryable events to FAILED. No retry selection of FAILED exists in this repository. Consequently an initial failure can strand the event before its configured DEAD threshold.

Implementation:

1. Either leave retryable rows PENDING with a future due time, or explicitly select due FAILED rows.
2. Add a claim/lease model using short transactions and `FOR UPDATE SKIP LOCKED` or equivalent atomic claims; current pending selection does not claim ownership across replicas.
3. Process each event independently so one handler transaction cannot roll back an entire polling batch.
4. Bound publish timeouts; preserve at-least-once delivery and make handler deduplication explicit.
5. Use PostgreSQL conflict-safe inserts (`ON CONFLICT DO NOTHING RETURNING`, for example) for inbox/processed-event deduplication. Catching a flushed integrity exception inside the same PostgreSQL transaction is not a reliable recovery strategy; `noRollbackFor` does not repair an aborted database transaction.
6. Add `checkout-events` to Kafka subscriptions; the ingestion service emits this topic but `KafkaRecoveryConsumer` does not subscribe to it.
7. Configure and test consumer retry/dead-letter behavior, including malformed envelopes. Outbox publication failure and downstream consumption failure are distinct states.

Acceptance: broker failure then recovery delivers an event, permanent failure reaches DEAD after the configured attempts, two publishers claim safely, and one poison event does not block unrelated work. Checkout events execute in Kafka mode.

### P0: reconciliation failure permits execution on stale state

Evidence: `payment/application/PaymentService.java:134` catches provider failure and returns the existing payment. `recovery/application/RecoveryOrchestrator.java:499` then continues if that cached payment is FAILED.

Implementation:

1. Return a typed reconciliation result containing verified state/freshness or an explicit unavailable result.
2. Defer external recovery actions when current payment state cannot be verified.
3. Persist retry timing and reason, with bounded backoff and escalation after the recovery window.
4. Distinguish authorization, capture, refund, and recovery attribution. Authorization should stop collection but should not automatically count as captured revenue.

Acceptance: timeout, 429, authentication error, or malformed provider response causes zero new orders, payment links, or customer messages. A subsequent verified result safely resumes or cancels recovery.

### P1: retries and successful interventions can leave incidents stuck

Evidence: `recovery/application/RecoveryOrchestrator.java`. `attemptsCount` increases only on success. A provider failure records the action FAILED and re-enters scheduling with the same attempt number; scheduling finds that existing idempotency key and returns. The incident can therefore remain SCHEDULED without an executable action. Conversely, after a successful action, the EXECUTING pipeline only advances when no action has succeeded; it does not reconcile payment state there or schedule a bounded follow-up.

Implementation:

1. Separate action execution retries from business recovery attempts. Define precisely when each counter advances.
2. Preserve the action's operation identity across retries of an uncertain external result; create a new business attempt only when warranted.
3. Introduce an explicit awaiting-payment state, deadline, and reconciliation timer after link/message/order creation.
4. Link replacement order/link/payment identifiers to the original incident. A new successful payment must resolve the original failed-payment incident even when its payment ID differs.
5. Continue tracking authorized incidents until capture or another definitive outcome; the current terminal LATE_AUTHORIZED status and open-incident lookup need reconciliation.
6. Make approval rejection resolve the incident; currently it updates the approval but leaves the incident awaiting approval. Apply locks/version checks to conflicting approval decisions.

Acceptance: transient failure recovers without a stranded incident; a sent link is followed to capture or expiry; replacement payment resolves the correct incident once; rejection leaves no active recovery.

### P1: database idempotency does not cover external effects

Evidence: `executeAction` performs provider calls while holding database locks in a transaction; provider creation methods have no action operation key parameter. `CommunicationService` supplies a fresh random message key on every send.

Risk requiring failure-injection validation: a provider accepts a request, then the process dies or the database commit fails. Retrying can repeat the external effect despite unique local action rows.

Implementation:

1. Persist operation intent, stable operation key, request fingerprint, lease, and attempt state in a short transaction.
2. Perform network I/O outside long database transactions; persist the outcome in a separate transaction with ownership/version checks.
3. Use documented provider idempotency where available. Otherwise persist provider correlation references, reconcile ambiguous outcomes, and require manual handling where a safe automatic retry cannot be established.
4. Give notifications a stable action/template/channel key with a unique database constraint.
5. Define a canonical lock order across action, incident, payment, and customer operations.
6. Start Temporal workflows from durable committed intents; the current launcher is called before the action transaction commits.

Acceptance: kill the process after provider acceptance but before local commit, restart, and demonstrate no duplicate externally visible action. Run two real `executeAction` calls concurrently against the same action.

### P1: policy controls and advertised strategies diverge

Evidence: `policy/application/PolicyEngine.java` gates DELAYED_RETRY with `allowPaymentLinks` instead of `allowDelayedRetry`, and does not gate UPI_RECOVERY equivalently to the link operation it performs. `RecoveryOrchestrator.dispatch` returns NOOP for unhandled strategies and then records success. Communication results are ignored while incident contact counts increase; blocked/failed sends can appear successful. All message strategies use DEMO_INBOX, and bounded discounts do not implement an actual discount amount/application path.

Implementation:

1. Use a strategy enum/registry with capability, prerequisites, policy checks, and a typed execution result.
2. Disable unsupported strategies and channels in candidate generation and the UI until implemented.
3. Check execution-time approval requirements, allowed channels, preferences, discount amount, customer cooldown, and retry/window constraints consistently.
4. Increment delivered-contact counts only for delivered/simulated outcomes according to explicit demo semantics; track blocked and failed outcomes separately.
5. Lock/reserve customer contact capacity so concurrent incidents cannot bypass cooldowns.
6. Replace hardcoded business hours and rupee formatting with merchant timezone/currency configuration. Inject `Clock` for deterministic boundary tests.

Acceptance: table-driven coverage for every strategy and policy switch; no unknown strategy produces SUCCEEDED; blocked sends do not increment successful-contact counts.

### P1: configured merchant credentials do not drive provider calls

Evidence: `merchant/api/IntegrationController.java` stores encrypted per-organization keys; `integration/razorpay/RazorpayPaymentProvider.java` constructs Basic authentication from global application properties, and `ProviderConfig` selects one global provider.

Implementation: add an organization/merchant/integration-scoped provider factory; resolve credentials and test/mock mode from the selected active integration; keep decrypted secrets short-lived; provide a verified connection test and rotation flow; reject inconsistent environment modes. Never silently use another merchant's environment keys.

Acceptance: two merchants call a stub provider with their own credentials; disabled integration cannot execute; mock outcomes remain clearly labeled.

### P1: session behavior is inconsistent across browser paths

Evidence: `apps/web/src/lib/auth.tsx` treats `/auth/me` as a flat user, while `AuthDtos.MeResponse` returns `{user, permissions, orgName}`. The frontend immediately redirects on 401 without using rotating refresh tokens. CSRF configuration uses Spring's default repository names while frontend/controller expect `ra_csrf` and `X-CSRF-Token`.

`AuthService.refresh` also has a revocation risk: family revocation is followed by an unchecked exception inside the same transaction, which can roll the revocation back. Refresh rotation lacks row locking/CAS, and user ACTIVE status is not rechecked there.

Implementation:

1. Correct the `/me` contract and hydrate user, permissions, and organization explicitly.
2. Select a coherent browser cookie flow; align CSRF cookie/header names and materialize tokens correctly.
3. Implement a single shared refresh operation for concurrent requests, retry once, then expire the session. Ensure an expired access token does not prevent the refresh route from reaching its handler.
4. Make refresh rotation atomic and commit family revocation even when denying reuse. Check account/membership eligibility.
5. Clear organization-specific query caches on logout/organization changes; hide unavailable actions using permissions while retaining server authorization.

Acceptance: page reload preserves identity/role; expiry refreshes once under concurrent requests; cookie mutations require valid CSRF; stolen rotated tokens revoke the family durably; disabled accounts cannot refresh.

### P1: headline analytics are not derived consistently

Evidence: `analytics/application/DashboardService.java:69` computes incremental revenue as recovered amount times the constant 0.3985. Recovery rate divides cumulative recovered amount by currently open revenue at risk, which can exceed 100%; CLOSED incidents are counted as recovered even though NO_ACTION can close an incident.

Implementation: define a versioned metric dictionary with cohort/date/currency/denominator rules; derive recovered revenue from confirmed attributed captures; separate closed-unrecovered from recovered; use persisted experiment results for simulated uplift and show incremental attribution as unavailable when no valid comparison exists. Store actual intervention costs. Read materialized aggregates/snapshots for dashboards rather than repeatedly aggregating live incident tables.

Acceptance: a hand-computed fixture containing recovered, no-action, open, authorized, and refunded cases matches every card; repeated capture events do not double count; currencies are not summed together.

### P2: AI resilience state is recreated per request

Evidence: `apps/ai-service/app/main.py:79` creates a new `LlmProvider` for each request. Its constructor creates both a circuit breaker and `httpx.Client`, so failures do not accumulate across requests and clients are not explicitly closed. Retry count is hardcoded despite a configuration field; nested dictionaries leave important output values weakly constrained.

Implementation: manage a provider/client in application lifespan and close it at shutdown; make shared breaker state concurrency-safe; align Java and Python total timeout/retry budgets; honor configured retry limits; replace dictionary ranking/date/time/channel outputs with bounded typed models and reject unknown/duplicate strategies. Authenticate or isolate the internal AI endpoint and bound request size/concurrency. Evaluate ranking quality against deterministic baseline using held-out fixtures; record latency, fallback reason, model/prompt version, and cost.

Acceptance: repeated failing requests open the breaker across requests, cooldown permits a controlled probe, invalid model responses always degrade safely, and total call time respects the caller's budget.

### P2: developer workflow, CI, and deployment need executable validation

- `Makefile setup` refers to nonexistent `requirements-dev.txt`; `make dev` starts only the API although it announces all services.
- Frontend lint prompts interactively; the CI web job does not invoke lint despite its name. Backend formatting is also not a CI gate.
- E2E visits the approval queue but does not approve/reject anything. `WorkerCrashRecoveryIT` inserts a duplicate row rather than crashing/restarting a worker. Existing concurrency tests exercise repository constraints rather than two orchestrator executions.
- Helm applies numeric UID 10001 across images that create users without explicitly matching that UID. Verify startup and filesystem permissions using actual images.
- The web image builds a proxy destination `http://api:8080`; Helm uses release-prefixed API service names. Validate the ingress routing and built rewrite destination together rather than assuming a runtime public variable changes them.
- Shared config/secrets are supplied broadly to services. Restrict each workload to the credentials it needs. Replace production development defaults with startup validation.
- Validate worker/API process roles, database migration ownership, probes, TLS, resource limits, network policies, backup restore, and rollback in staging. Keep deployment claims marked unverified until demonstrated.

## Delivery plan

Estimates assume one experienced engineer, an available Docker/staging environment, and focused work. They are planning ranges, not commitments. Integration discoveries may extend them. Write regression tests with each fix; do not postpone correctness tests to the final phase.

| Phase | Approximate effort | Deliverables | Exit gate |
|---|---:|---|---|
| 0. Reproducible baseline | 1–2 days | Fix setup/dev/lint, document Windows and Unix commands, capture test reports, replace unsupported completion claims | Fresh checkout installs and checks noninteractively |
| 1. Tenant isolation | 2–4 days | Scoped DLQ/system queries, transactional replay, ownership checks, tenant constraint migration and audit | Two-tenant API integration matrix passes |
| 2. Webhook and event correctness | 3–5 days | Real payload DTOs/redaction, payment transition fixes, conflict-safe inbox, outbox retries/claims, checkout subscription | Signed provider fixture reaches domain state in inline and Kafka modes |
| 3. Recovery lifecycle and external effects | 5–8 days | Typed reconciliation, execution intent/lease model, bounded retries, ambiguous-result reconciliation, replacement-payment mapping, durable workflow start | Crash-window and concurrent execution tests pass |
| 4. Policy, approvals, integrations, auth | 4–7 days | Strategy registry, actual channel outcomes, approval lifecycle, merchant-scoped provider, coherent session/CSRF/refresh | Every supported action is authorized, attributed, and recoverable after reload |
| 5. Analytics and operational UI | 3–5 days | Correct cohort metrics, snapshot reads, visible metric definitions, permissions/errors, validated policy forms, connection test | Dashboard matches ledger fixtures; critical browser flows pass |
| 6. AI service hardening | 2–3 days | Shared provider lifecycle/breaker, strict nested schemas, bounded budgets, evaluation report | Outage and malformed-output scenarios meet budgets |
| 7. Deployment and failure validation | 4–7 days | Real integration/chaos jobs, Helm/image checks, isolated secrets, dashboards, load evidence, backup/rollback drill | Staging release passes documented operational gates |

Total sequential effort: approximately 24–41 engineering days, or roughly 5–9 focused working weeks allowing for integration fixes. Phases 1–3 are the immediate correctness milestone. Avoid adding providers, voice, SSO, or microservice extraction until those gates pass.

### Suggested pull request sequence

1. Build/setup/lint baseline and corrected status document.
2. Tenant-scoped DLQ/system services and ownership constraints.
3. Razorpay normalization, allowlisted persistence, and payment state regressions.
4. Outbox retry/claim model, inbox conflict-safe inserts, and Kafka consumer recovery.
5. Fail-closed reconciliation and explicit action outcomes.
6. Durable operation intent/leases and restart-safe side effects.
7. Incident/payment attribution, follow-up timers, and approval completion.
8. Strategy/policy coverage and organization-scoped provider credentials.
9. Session contract, refresh rotation, CSRF, and browser permission handling.
10. Ledger-derived metrics and snapshot-backed dashboard queries.
11. AI lifecycle, output contracts, and failure-budget evaluation.
12. Deployment validation, realistic failure tests, load evidence, and runbooks.

### Schema rollout and recovery

Use additive Flyway migrations first: operation intent/lease fields or tables, provider correlation references, processing deduplication, and required tenant indexes. Backfill and report invalid relationships before enabling constraints. Recover stranded FAILED outbox rows through an audited migration or repair command with a dry-run report; do not mass-replay unknown historical side effects. Release consumers that understand both old and new event versions before changing producers. Keep money movement and outbound communications disabled during uncertain state migration. Test rollback with the expanded schema and preserve operation identities across versions.

### Release acceptance scenarios

1. Provider-shaped failed payment -> persisted incident -> allowed action -> linked replacement capture -> exactly one attributed recovery.
2. Original payment authorizes immediately before execution -> recovery stopped; later capture updates confirmed revenue.
3. Reconciliation unavailable -> no external action; verified retry resumes safely.
4. Duplicate/reordered webhooks -> stable payment/incident state and no duplicate financial attribution.
5. Broker outage/poison event/two publishers -> bounded retries, eventual processing or inspectable dead letter.
6. Crash after provider acceptance before commit -> restart resolves ambiguous result without blind duplicate creation.
7. Policy/opt-out changes before execution -> blocked action, accurate contact counters, complete audit record.
8. Approval approve/reject race -> one committed decision and a consistent incident outcome.
9. Organization A submits organization B's IDs -> no disclosure or mutation.
10. Browser reload/access expiry/refresh reuse -> correct identity, CSRF enforcement, and durable revocation.
11. Known ledger fixture -> correct amount, rate, costs, and currency-separated reporting.
12. Fresh environment deployment -> passing probes, working API routing, least-privilege secrets, backup restore, and rollback evidence.

Operational success should be measured using observed staging data: webhook acknowledgment latency, processing lag and oldest pending event age, stuck-action age, reconciliation freshness, dead-letter rate, database lock/pool pressure, and AI latency/fallback reasons. Establish a baseline before setting and claiming throughput or latency targets.
