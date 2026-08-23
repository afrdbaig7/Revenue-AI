# Contributing

Thanks for contributing to RecoverAI. This project cares about payment correctness,
safety, and auditability — code reviews are strict by design.

## Ground rules

1. **Never** commit secrets, `.env`, or credentials.
2. **Never** use floats/doubles for money — `BigInteger`/`long` minor units only.
3. **AI output is untrusted input** — anything from an LLM must pass schema
   validation and can never execute financial actions directly.
4. Every state transition, decision, and external action needs an audit event.
5. Every new external API call must match official documentation — no invented
   endpoints (especially Razorpay).
6. Tests are mandatory for: policy evaluation, state machines, idempotency,
   EV math, webhook signature handling, permission checks.

## Development setup

```bash
make setup      # env + deps
make infra-up   # docker compose infra
make migrate && make seed
make dev        # api :8080, ai :8100, web :3000
make test       # unit tests
make test-it    # integration tests (needs Docker)
```

## Workflow

1. Create a branch from `main`: `feat/<domain>-<change>`.
2. Implement with tests; keep changes small and reviewable.
3. Run `make lint && make test` locally.
4. Open a PR describing the change, the safety implications, and test evidence.

## Code style

- Java: Maven + Spotless (google-java-format); domain packages (`api/application/
  domain/infrastructure` per domain). No god classes, no business logic in controllers.
- Python: ruff + mypy; Pydantic schemas for every AI output.
- TypeScript: ESLint + Prettier; typed API client generated from the OpenAPI spec
  (`packages/contracts`).

## Review checklist

- [ ] Tenant scoping on every new query
- [ ] Idempotency for every side-effecting operation
- [ ] Audit event for every meaningful transition/action
- [ ] Money handled in minor units
- [ ] No secrets logged or returned
- [ ] Tests cover the happy path and the failure path
