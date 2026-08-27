# ADR-006: AI as Advisory System

**Status:** Accepted

## Context
LLMs are unreliable for exact execution (non-deterministic, prompt-injectable,
hallucination-prone). Letting an LLM directly trigger payments would be unsafe and
unauditable.

## Decision
**AI recommends. Deterministic software authorizes and executes.**

- AI service responsibilities: diagnosis, strategy ranking, explanation generation,
  communication-content generation, promise-to-pay extraction.
- Platform responsibilities (AI can never touch): payment-state verification,
  authorization, retries, idempotency, spending/discount/contact limits, workflow
  transitions, opt-outs, webhook verification, recovery windows, stopping rules,
  Razorpay API execution.
- All LLM output is treated as **untrusted input**: validated against strict Pydantic
  schemas; deterministic fallback on malformed/timeout/low-confidence output.
- AI has no write path to financial state — it cannot even call the payment API.

## Alternatives considered
- LLM as agent with tool calls: rejected — unproven reliability for money movement,
  poor auditability, prompt-injection risk.
- Pure rule-based system (no AI): safe but loses the differentiation and
  explainability value; AI augments, not replaces, determinism.

## Consequences
- Every AI contribution is recorded (model version, prompt version, confidence,
  evidence) in `incident_diagnoses` / `recovery_decisions`.
- AI outage degrades to deterministic mode without pausing recovery.
- Demo works with zero LLM credentials (deterministic fallback).
