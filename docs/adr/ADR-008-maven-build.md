# ADR-008: Maven for the Java Build

**Status:** Accepted

## Context
The spec allows Gradle Kotlin DSL or Maven "with clear justification". The repo must be
predictable in CI, easy to review, and low-risk to maintain by a small team.

## Decision
**Maven** with a single `pom.xml` (no multi-module split yet — one deployable API
artifact + one worker artifact can share sources via the same module for now).
Rationale:
- Declarative, deterministic builds; the most widely understood by reviewers.
- Maven Wrapper pins the toolchain (Java 21) — no local Gradle distribution downloads.
- Maven Central metadata + `versions-maven-plugin` make dependency hygiene mechanical.
- Spotless (license headers/format), Surefire (unit), Failsafe (IT with Testcontainers),
  and Spring Boot plugin integrate cleanly.

## Alternatives considered
- Gradle Kotlin DSL: more expressive, faster incremental builds — but build-script
  logic is harder to review and Kotlin DSL churn adds maintenance risk for this repo.

## Consequences
- Slightly more verbose dependency declarations than Gradle.
- CI pipeline is a simple `mvn verify` chain; caching via Maven repo in GitHub Actions.
