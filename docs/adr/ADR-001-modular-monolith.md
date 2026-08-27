# ADR-001: Modular Monolith over Microservices

**Status:** Accepted

## Context
RecoverAI must demonstrate production engineering while remaining shippable by a small
team. Splitting into microservices prematurely would multiply operational complexity
(deployment, transactions, testing) without proportional benefit at this stage.

## Decision
A modular monolith: one Spring Boot service containing all transactional business
domains (auth, tenant, payment, incident, policy, recovery, audit, analytics,
experiment), with **strict package-level domain boundaries** (`auth/`, `payment/`,
`incident/`, ... each with `api/application/domain/infrastructure` layers). The AI
decision service is a **separate process** from day one because it has different
runtime characteristics (stateless, GPU/LLM-bound, must fail independently).

## Alternatives considered
- Full microservices (per-domain services): rejected — distributed-transaction tax,
  no team to operate it, no scale requirement yet.
- Single fat service with no boundaries: rejected — would block future extraction.

## Consequences
- Transactional invariants (idempotency, audit, state machines) are enforced in one
  transaction — simpler and safer.
- Domains communicate via Spring events + outbox; extraction to services later is
  mechanical because boundaries are already clean.
- Deployment is one artifact + AI service + workers.
